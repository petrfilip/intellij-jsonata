package cz.tix.jsonata.completion

import com.dashjoin.jsonata.Jsonata
import com.dashjoin.jsonata.json.Json
import com.intellij.openapi.progress.ProcessCanceledException

/**
 * Resolves field names for a JSONata path context by evaluating the prefix against the bound JSON.
 *
 * Parsing is cached for the most recently used document. Prefix evaluation is bounded so completion
 * cannot monopolize an IDE worker thread. IntelliJ cancellation is always propagated unchanged.
 */
class JsonataFieldKeys(
    private val evalTimeoutMillis: Long = 250L,
    private val evalMaxDepth: Int = 200,
) {
    private var cachedText: String? = null
    private var cachedData: Any? = null

    @Synchronized
    fun keys(containerExpr: String?, jsonText: String): List<String> {
        val data = dataFor(jsonText) ?: return emptyList()

        evaluate(containerExpr, data)?.let { return keysOf(it) }

        if (containerExpr.isNullOrBlank()) return keysOf(data)
        val segments = containerExpr.split('.').map { it.trim() }
        if (segments.all { IDENT.matches(it) }) {
            var current: Any? = data
            for (segment in segments) current = stepInto(current, segment) ?: return emptyList()
            return keysOf(current)
        }
        return emptyList()
    }

    private fun evaluate(containerExpr: String?, data: Any?): Any? {
        if (containerExpr.isNullOrBlank()) return data
        return try {
            val expr = Jsonata.jsonata(containerExpr)
            val frame = expr.createFrame()
            frame.setRuntimeBounds(evalTimeoutMillis, evalMaxDepth)
            expr.evaluate(data, frame)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    private fun dataFor(jsonText: String): Any? {
        if (jsonText == cachedText) return cachedData
        val parsed = try {
            jsonText.takeIf { it.isNotBlank() }?.let { Json.parseJson(it) }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
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

        fun keysOf(node: Any?): List<String> = when (node) {
            is Map<*, *> -> node.keys.map { it.toString() }
            is List<*> -> node.filterIsInstance<Map<*, *>>()
                .flatMap { m -> m.keys.map { it.toString() } }
                .distinct()
            else -> emptyList()
        }
    }
}
