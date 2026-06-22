package cz.tix.jsonata.engine

import com.dashjoin.jsonata.Jsonata
import com.dashjoin.jsonata.json.Json
import com.intellij.openapi.progress.ProcessCanceledException
import cz.tix.jsonata.api.JsonataFn
import java.util.IdentityHashMap

/**
 * A custom function bound into the evaluator: callable as `$name(...)` in expressions.
 *
 * @param name function name *without* the leading `$`.
 * @param fn the JVM implementation invoked per call.
 */
data class CustomFunction(val name: String, val fn: JsonataFn)

/**
 * Thin wrapper around the dashjoin JSONata engine. Pure / off-EDT safe.
 *
 * Supports custom functions in two ways:
 *  - [prelude]: JSONata-defined functions (`$f := function(...) {...};`) prepended to the expression;
 *  - [functions]: JVM functions bound into the per-call frame as `$name(...)`.
 *
 * Compilation and evaluation are guarded separately, and runtime bounds protect the IDE from a
 * runaway JSONata loop. (Note: the bound JVM functions run unsandboxed — see the loader's gating.)
 */
class JsonataEvaluator(
    private val timeoutMillis: Long = 2_000L,
    private val maxRecursionDepth: Int = 500,
) {
    /** Outcome of [evaluate]: either a rendered result or a phase-tagged failure. */
    sealed interface Result {
        /**
         * @param pretty the rendered JSON result (empty for a blank expression / no match).
         * @param note non-error advisory shown alongside the result (e.g. "no match (undefined)").
         */
        data class Success(val pretty: String, val note: String? = null) : Result

        /** @param phase the pipeline stage that failed; [Phase.label] is the user-facing prefix. */
        data class Failure(val message: String, val phase: Phase) : Result
    }

    /** Which pipeline stage produced a [Result.Failure]; [label] prefixes the error in the UI. */
    enum class Phase(val label: String) {
        JSON("JSON"), PRELUDE("Custom functions"), EXPRESSION("Expression"), EVALUATION("Evaluation")
    }

    /**
     * Runs the pipeline: parse [jsonText] → validate [prelude] alone → compile `prelude + expression`
     * together → evaluate against the data with the runtime bounds applied → render the result. A
     * rendering failure folds into [Phase.EVALUATION] rather than carrying its own phase.
     *
     * Gotchas:
     *  - blank [jsonText] ⇒ null data (JSONata *undefined* context);
     *  - blank [expression] ⇒ empty [Result.Success] (nothing to evaluate);
     *  - a Java-null engine result ⇒ empty success with the "no match (undefined)" note;
     *  - [functions] are bound into the per-call frame with a null (variadic) signature.
     *
     * @return a [Result.Success] (pretty-printed) or a [Result.Failure] tagged with the failing [Phase].
     * @throws ProcessCanceledException re-thrown unchanged from any phase so the platform can cancel
     *   the (off-EDT) evaluation; every other exception is captured as a [Result.Failure].
     */
    fun evaluate(
        jsonText: String,
        expression: String,
        prelude: String = "",
        functions: List<CustomFunction> = emptyList(),
    ): Result {
        val data: Any? = try {
            if (jsonText.isBlank()) null else Json.parseJson(jsonText)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            return Result.Failure("Invalid JSON input: ${message(e)}", Phase.JSON)
        }

        if (expression.isBlank()) return Result.Success("")

        // Validate the prelude on its own first so its errors aren't blamed on the main expression.
        if (prelude.isNotBlank()) {
            try {
                Jsonata.jsonata("($prelude\ntrue)")
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                return Result.Failure(message(e), Phase.PRELUDE)
            }
        }

        val source = if (prelude.isBlank()) expression else "($prelude\n$expression)"
        val compiled: Jsonata = try {
            Jsonata.jsonata(source)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            return Result.Failure(message(e), Phase.EXPRESSION)
        }

        val output: Any? = try {
            val frame = compiled.createFrame()
            frame.setRuntimeBounds(timeoutMillis, maxRecursionDepth)
            for (function in functions) {
                val callable = Jsonata.JFunctionCallable { _, args ->
                    val apiArgs = args.orEmpty().map { fromEngineValue(it) }
                    toEngineValue(function.fn.call(apiArgs))
                }
                // 2nd arg is a JSONata signature string; null = no arg-type validation (variadic).
                frame.bind(function.name, Jsonata.JFunction(callable, null as String?))
            }
            compiled.evaluate(data, frame)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            return Result.Failure(message(e), Phase.EVALUATION)
        }

        return if (output == null) {
            Result.Success("", "no match (undefined)")
        } else {
            try {
                Result.Success(JsonRenderer.render(output))
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                Result.Failure("Unable to render result: ${message(e)}", Phase.EVALUATION)
            }
        }
    }

    private fun message(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: e.toString()

    // Converts an engine value back to a plain JVM tree for a custom function's args: the engine's
    // JSON-null sentinel becomes a real Kotlin null. No cycle guard here (unlike toEngineValue):
    // argument trees come from Json.parseJson or the engine's own structures, which are acyclic.
    private fun fromEngineValue(value: Any?): Any? = when {
        value === Jsonata.NULL_VALUE -> null
        value is Map<*, *> -> value.entries.associateTo(LinkedHashMap<Any?, Any?>()) { (k, v) ->
            k to fromEngineValue(v)
        }
        value is List<*> -> value.map { fromEngineValue(it) }
        else -> value
    }

    private fun toEngineValue(value: Any?): Any? =
        toEngineValue(value, nested = false, seen = IdentityHashMap())

    // Null asymmetry: a TOP-LEVEL null returns Java null = JSONata undefined (so the function result
    // simply drops out), but a null INSIDE a Map/List becomes NULL_VALUE = a real JSON null element.
    // `seen` is an IdentityHashMap cycle guard so a self-referential return throws instead of looping.
    // A cyclic return throws IllegalArgumentException, which the EVALUATION-phase catch turns into a Failure.
    private fun toEngineValue(value: Any?, nested: Boolean, seen: IdentityHashMap<Any, Unit>): Any? = when (value) {
        null -> if (nested) Jsonata.NULL_VALUE else null
        is Map<*, *> -> {
            if (seen.put(value, Unit) != null) {
                throw IllegalArgumentException("Custom function returned a cyclic map")
            }
            try {
                value.entries.associateTo(LinkedHashMap<Any?, Any?>()) { (k, v) ->
                    // A JSON object cannot have a null key; reject rather than render it as "null".
                    if (k == null) throw IllegalArgumentException("Custom function returned a map with a null key")
                    k to toEngineValue(v, nested = true, seen)
                }
            } finally {
                seen.remove(value)
            }
        }
        is List<*> -> {
            if (seen.put(value, Unit) != null) {
                throw IllegalArgumentException("Custom function returned a cyclic list")
            }
            try {
                value.map { toEngineValue(it, nested = true, seen) }
            } finally {
                seen.remove(value)
            }
        }
        else -> value
    }
}
