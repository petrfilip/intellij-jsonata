package cz.tix.jsonata.completion

import com.dashjoin.jsonata.Jsonata
import com.dashjoin.jsonata.json.Json

/**
 * Resolves the field names to offer for a given path context, using the **"evaluate the prefix"**
 * strategy: the container expression (everything before the current `.`) is evaluated against the
 * bound JSON with the real engine, and the keys of the *actual* resulting value are returned. This
 * is accurate for predicates, function calls and chaining — things a static tree walk can't model.
 *
 * Safeguards:
 *  - the parsed JSON is cached and only re-parsed when the source text changes;
 *  - evaluation is sandboxed with a tight runtime bound (much shorter than the main playground
 *    eval) so completion never hangs;
 *  - if evaluation fails (unbalanced/invalid prefix, lambda-local variables, timeout, no match) it
 *    falls back to a static dotted-path walk, and otherwise yields nothing rather than guessing.
 *
 * dashjoin-only (no IntelliJ deps) — unit-tested in JsonataFieldKeysTest.
 *
 * @param evalTimeoutMillis runtime bound for the prefix evaluation — deliberately tight so completion never hangs.
 * @param evalMaxDepth recursion bound for the prefix evaluation — likewise kept low.
 */
class JsonataFieldKeys(
    private val evalTimeoutMillis: Long = 250L,
    private val evalMaxDepth: Int = 200,
) {
    private var cachedText: String? = null
    private var cachedData: Any? = null

    /**
     * Field names to offer for the given path context, in two stages: engine-evaluate the prefix and
     * take its keys; failing that, statically walk a plain identifier-only dotted path; otherwise empty.
     *
     * `@Synchronized` because a single instance is shared across concurrent completion threads: the
     * whole key resolution (the [dataFor] cache read AND the engine evaluation that consumes the
     * cached data) must run under one monitor. Java monitors are reentrant, so the nested
     * `@Synchronized` [dataFor] call does not deadlock.
     *
     * @param containerExpr the expression before the `.` (null/blank ⇒ the JSON root).
     */
    @Synchronized
    fun keys(containerExpr: String?, jsonText: String): List<String> {
        val data = dataFor(jsonText) ?: return emptyList()

        evaluate(containerExpr, data)?.let { return keysOf(it) }

        // Fallback: static walk for a plain dotted path (covers cases the engine wouldn't evaluate).
        if (containerExpr.isNullOrBlank()) return keysOf(data)
        val segments = containerExpr.split('.').map { it.trim() }
        if (segments.all { IDENT.matches(it) }) {
            var current: Any? = data
            for (segment in segments) current = stepInto(current, segment) ?: return emptyList()
            return keysOf(current)
        }
        return emptyList()
    }

    /**
     * Engine-evaluates [containerExpr] against [data], returning the resulting value, or `null` on any
     * failure (caught [Throwable]) — that `null` is the signal for [keys] to fall through to the static
     * dotted-path walk.
     */
    private fun evaluate(containerExpr: String?, data: Any?): Any? {
        if (containerExpr.isNullOrBlank()) return data
        return try {
            val expr = Jsonata.jsonata(containerExpr)
            val frame = expr.createFrame()
            frame.setRuntimeBounds(evalTimeoutMillis, evalMaxDepth)
            expr.evaluate(data, frame)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns the cached parsed JSON, re-parsing only when [jsonText] changes. Still `@Synchronized`
     * for safety; it is normally entered (reentrantly) from the already-synchronized [keys].
     */
    @Synchronized
    private fun dataFor(jsonText: String): Any? {
        if (jsonText == cachedText) return cachedData
        val parsed = try {
            jsonText.takeIf { it.isNotBlank() }?.let { Json.parseJson(it) }
        } catch (_: Throwable) {
            null
        }
        cachedData = parsed
        cachedText = jsonText
        return cachedData
    }

    private fun stepInto(node: Any?, key: String): Any? = when (node) {
        is Map<*, *> -> node[key]
        is List<*> -> node.mapNotNull { (it as? Map<*, *>)?.get(key) }.ifEmpty { null }
        else -> null
    }

    companion object {
        private val IDENT = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** Keys exposed by [node]: an object's keys, the distinct union of an array's member-object keys, else empty. */
        fun keysOf(node: Any?): List<String> = when (node) {
            is Map<*, *> -> node.keys.map { it.toString() }
            is List<*> -> node.filterIsInstance<Map<*, *>>()
                .flatMap { m -> m.keys.map { it.toString() } }
                .distinct()
            else -> emptyList()
        }
    }
}
