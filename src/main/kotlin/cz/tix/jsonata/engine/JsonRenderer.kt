package cz.tix.jsonata.engine

import com.dashjoin.jsonata.Jsonata
import java.math.BigDecimal
import java.util.IdentityHashMap

/**
 * Pretty-prints the plain Java object tree produced by the dashjoin JSONata engine
 * (Map / List / String / Number / Boolean / null) as indented JSON.
 *
 * No external JSON library is needed, and it lets us control two JSONata-specific quirks:
 *  - [Jsonata.NULL_VALUE] is the sentinel for an actual JSON `null` (Java `null` means *undefined*).
 *  - integral doubles (e.g. `24.0`) are rendered as `24`, matching jsonata.js / try.jsonata.org.
 */
object JsonRenderer {

    /**
     * Pretty-prints [value] (a Map/List/String/Number/Boolean/null tree) as 2-space-indented JSON.
     *
     * @throws IllegalArgumentException if the tree contains a cycle.
     */
    fun render(value: Any?): String =
        buildString { write(value, this, 0, IdentityHashMap()) }

    private fun write(value: Any?, sb: StringBuilder, indent: Int, seen: IdentityHashMap<Any, Unit>) {
        when {
            value == null || value === Jsonata.NULL_VALUE -> sb.append("null")
            value is Map<*, *> -> writeMap(value, sb, indent, seen)
            value is List<*> -> writeList(value, sb, indent, seen)
            value is CharSequence -> writeString(value.toString(), sb)
            value is Boolean -> sb.append(value.toString())
            value is Number -> sb.append(renderNumber(value))
            else -> writeString(value.toString(), sb)
        }
    }

    private fun writeMap(map: Map<*, *>, sb: StringBuilder, indent: Int, seen: IdentityHashMap<Any, Unit>) {
        if (seen.put(map, Unit) != null) {
            throw IllegalArgumentException("Cannot render cyclic map")
        }
        try {
            if (map.isEmpty()) { sb.append("{}"); return }
            sb.append("{\n")
            val entries = map.entries.toList()
            entries.forEachIndexed { i, e ->
                indent(sb, indent + 1)
                writeString(e.key.toString(), sb)
                sb.append(": ")
                write(e.value, sb, indent + 1, seen)
                if (i < entries.lastIndex) sb.append(',')
                sb.append('\n')
            }
            indent(sb, indent)
            sb.append('}')
        } finally {
            seen.remove(map)
        }
    }

    private fun writeList(list: List<*>, sb: StringBuilder, indent: Int, seen: IdentityHashMap<Any, Unit>) {
        if (seen.put(list, Unit) != null) {
            throw IllegalArgumentException("Cannot render cyclic list")
        }
        try {
            if (list.isEmpty()) { sb.append("[]"); return }
            sb.append("[\n")
            list.forEachIndexed { i, item ->
                indent(sb, indent + 1)
                write(item, sb, indent + 1, seen)
                if (i < list.lastIndex) sb.append(',')
                sb.append('\n')
            }
            indent(sb, indent)
            sb.append(']')
        } finally {
            seen.remove(list)
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c < ' ') sb.append("\\u").append("%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private fun renderNumber(n: Number): String = when (n) {
        is Double -> when {
            !n.isFinite() -> "null" // JSON has no Infinity/NaN
            // Only take the integer path for exactly-representable integers. Beyond 2^53 a double
            // cannot hold every integer, so BigInteger would print the spurious binary expansion
            // (e.g. 1e30 -> 1000000000000000019884624838656); fall back to the standard double
            // rendering, which is valid JSON (e.g. "1.0E30") and carries no fabricated digits.
            n == Math.floor(n) && Math.abs(n) <= MAX_EXACT_INTEGER ->
                BigDecimal(n).toBigInteger().toString() // integral, no scientific notation
            else -> n.toString()
        }
        is Float -> renderNumber(n.toDouble())
        else -> n.toString()
    }

    /** 2^53: largest magnitude at which every integer is exactly representable as a [Double]. */
    private const val MAX_EXACT_INTEGER = 9007199254740992.0

    private fun indent(sb: StringBuilder, level: Int) {
        repeat(level) { sb.append("  ") }
    }
}
