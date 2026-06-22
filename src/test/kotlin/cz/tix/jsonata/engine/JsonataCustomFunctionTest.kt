package cz.tix.jsonata.engine

import com.intellij.openapi.progress.ProcessCanceledException
import cz.tix.jsonata.api.JsonataFn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonataCustomFunctionTest {

    private val eval = JsonataEvaluator()

    @Test
    fun `bound jvm function is callable and returns a string`() {
        val fns = listOf(CustomFunction("greet", JsonataFn { args -> "Hello, ${args.firstOrNull()}" }))
        val r = eval.evaluate("{}", "\$greet('Petr')", functions = fns)
        assertEquals("\"Hello, Petr\"", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `bound function receives parsed numeric args`() {
        val fns = listOf(CustomFunction("add", JsonataFn { args -> (args[0] as Number).toDouble() + (args[1] as Number).toDouble() }))
        val r = eval.evaluate("{}", "\$add(2, 3)", functions = fns)
        assertEquals("5", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `bound function can operate on JSON data passed from the expression`() {
        val json = """{"items":[1,2,3,4]}"""
        val fns = listOf(
            CustomFunction("sumAll", JsonataFn { args ->
                (args[0] as List<*>).filterIsInstance<Number>().sumOf { it.toDouble() }
            }),
        )
        val r = eval.evaluate(json, "\$sumAll(items)", functions = fns)
        assertEquals("10", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `bound function receives json null argument as kotlin null`() {
        val fns = listOf(
            CustomFunction("classify", JsonataFn { args ->
                if (args.singleOrNull() == null) "null" else args.single()!!.javaClass.name
            }),
        )

        val r = eval.evaluate("{}", "\$classify(null)", functions = fns)

        assertEquals("\"null\"", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `bound function receives nested json null as kotlin null`() {
        val json = """{"item":{"present":null}}"""
        val fns = listOf(
            CustomFunction("classify", JsonataFn { args ->
                val item = args.single() as Map<*, *>
                if (item["present"] == null) "null" else "other"
            }),
        )

        val r = eval.evaluate(json, "\$classify(item)", functions = fns)

        assertEquals("\"null\"", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `cyclic custom function result is reported as evaluation failure`() {
        val cyclic = ArrayList<Any?>()
        cyclic.add(cyclic)
        val fns = listOf(CustomFunction("cycle", JsonataFn { cyclic }))

        val r = eval.evaluate("{}", "\$cycle()", functions = fns)

        assertTrue(r is JsonataEvaluator.Result.Failure)
        r as JsonataEvaluator.Result.Failure
        assertEquals(JsonataEvaluator.Phase.EVALUATION, r.phase)
        assertTrue(r.message.contains("cyclic", ignoreCase = true), "was: ${r.message}")
    }

    @Test
    fun `process cancellation from bound function is rethrown`() {
        val fns = listOf(CustomFunction("cancel", JsonataFn { throw ProcessCanceledException() }))

        assertThrows(ProcessCanceledException::class.java) {
            eval.evaluate("{}", "\$cancel()", functions = fns)
        }
    }

    @Test
    fun `prelude defined function is callable`() {
        val r = eval.evaluate("{}", "\$double(21)", prelude = "\$double := function(\$x){ \$x * 2 };")
        assertEquals("42", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `invalid prelude is reported as PRELUDE phase`() {
        val r = eval.evaluate("{}", "1", prelude = "\$bad := function(")
        assertTrue(r is JsonataEvaluator.Result.Failure)
        assertEquals(JsonataEvaluator.Phase.PRELUDE, (r as JsonataEvaluator.Result.Failure).phase)
    }

    @Test
    fun `prelude and jvm functions combine`() {
        val fns = listOf(CustomFunction("upper", JsonataFn { args -> args[0].toString().uppercase() }))
        val r = eval.evaluate(
            "{}",
            "\$upper(\$tag('x'))",
            prelude = "\$tag := function(\$s){ '<' & \$s & '>' };",
            functions = fns,
        )
        assertEquals("\"<X>\"", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `expression still works with no custom functions`() {
        val r = eval.evaluate("""{"a":1}""", "a")
        assertEquals("1", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `multiple bound functions are all callable in one expression`() {
        val fns = listOf(
            CustomFunction("inc", JsonataFn { args -> (args[0] as Number).toDouble() + 1 }),
            CustomFunction("dec", JsonataFn { args -> (args[0] as Number).toDouble() - 1 }),
        )
        val r = eval.evaluate("{}", "\$inc(1) + \$dec(10)", functions = fns)
        assertEquals("11", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `custom function returning top-level null yields undefined no-match`() {
        // Top-level null returns Java null = JSONata undefined, so the result simply drops out.
        val fns = listOf(CustomFunction("nada", JsonataFn { null }))
        val r = eval.evaluate("{}", "\$nada()", functions = fns) as JsonataEvaluator.Result.Success
        assertEquals("", r.pretty)
        assertEquals("no match (undefined)", r.note)
    }

    @Test
    fun `custom function returning a map with a null value renders json null`() {
        // A null INSIDE a returned map becomes NULL_VALUE = a real JSON null element.
        val fns = listOf(CustomFunction("obj", JsonataFn { linkedMapOf("a" to 1, "b" to null) }))
        val r = eval.evaluate("{}", "\$obj()", functions = fns) as JsonataEvaluator.Result.Success
        assertTrue(r.pretty.contains("\"b\": null"), "was: ${r.pretty}")
    }

    @Test
    fun `cyclic map from custom function is reported as evaluation failure`() {
        val cyclic = linkedMapOf<String, Any?>()
        cyclic["self"] = cyclic
        val fns = listOf(CustomFunction("cycle", JsonataFn { cyclic }))

        val r = eval.evaluate("{}", "\$cycle()", functions = fns)

        assertTrue(r is JsonataEvaluator.Result.Failure)
        r as JsonataEvaluator.Result.Failure
        assertEquals(JsonataEvaluator.Phase.EVALUATION, r.phase)
        assertTrue(r.message.contains("cyclic", ignoreCase = true), "was: ${r.message}")
    }

    @Test
    fun `non-cancellation exception from bound function surfaces as evaluation failure`() {
        // Only ProcessCanceledException is rethrown; every other exception is captured as a Failure.
        val fns = listOf(CustomFunction("boom", JsonataFn { throw IllegalStateException("kaboom") }))

        val r = eval.evaluate("{}", "\$boom()", functions = fns)

        assertTrue(r is JsonataEvaluator.Result.Failure, "was: $r")
        assertEquals(JsonataEvaluator.Phase.EVALUATION, (r as JsonataEvaluator.Result.Failure).phase)
    }

    @Test
    fun `null map key from custom function is reported as evaluation failure`() {
        // A JSON object cannot have a null key; rejecting it (like the cyclic case) beats silently
        // emitting a literal "null" key indistinguishable from a real "null" string key.
        val fns = listOf(CustomFunction("nullkey", JsonataFn { linkedMapOf<Any?, Any?>(null to 1) }))

        val r = eval.evaluate("{}", "\$nullkey()", functions = fns)

        assertTrue(r is JsonataEvaluator.Result.Failure, "was: $r")
        r as JsonataEvaluator.Result.Failure
        assertEquals(JsonataEvaluator.Phase.EVALUATION, r.phase)
        assertTrue(r.message.contains("null key", ignoreCase = true), "was: ${r.message}")
    }
}
