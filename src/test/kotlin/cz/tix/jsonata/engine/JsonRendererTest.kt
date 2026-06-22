package cz.tix.jsonata.engine

import com.dashjoin.jsonata.Jsonata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Pure unit tests for [JsonRenderer] — verifies the exact whitespace/indentation of the pretty
 * printer over a plain Java object tree. Depends only on the dashjoin engine (for NULL_VALUE).
 */
class JsonRendererTest {

    @Test
    fun `integer renders verbatim`() {
        assertEquals("24", JsonRenderer.render(24))
    }

    @Test
    fun `integral double renders without decimal point`() {
        assertEquals("24", JsonRenderer.render(24.0))
    }

    @Test
    fun `negative integral double renders without decimal point`() {
        assertEquals("-7", JsonRenderer.render(-7.0))
    }

    @Test
    fun `fractional double keeps its value`() {
        assertEquals("3.14", JsonRenderer.render(3.14))
    }

    @Test
    fun `long renders verbatim`() {
        assertEquals("9007199254740993", JsonRenderer.render(9007199254740993L))
    }

    @Test
    fun `booleans render as literals`() {
        assertEquals("true", JsonRenderer.render(true))
        assertEquals("false", JsonRenderer.render(false))
    }

    @Test
    fun `java null renders as null`() {
        assertEquals("null", JsonRenderer.render(null))
    }

    @Test
    fun `jsonata NULL_VALUE renders as null`() {
        assertEquals("null", JsonRenderer.render(Jsonata.NULL_VALUE))
    }

    @Test
    fun `plain string is quoted`() {
        assertEquals("\"x\"", JsonRenderer.render("x"))
    }

    @Test
    fun `string escaping covers quotes newline tab backslash and returns`() {
        // input: "  (quote), \  (backslash), \n, \r, \t   => each escaped
        val input = "\"\\\n\r\t"
        assertEquals("\"\\\"\\\\\\n\\r\\t\"", JsonRenderer.render(input))
    }

    @Test
    fun `backspace escaped and other control chars use unicode escape`() {
        assertEquals("\"\\b\"", JsonRenderer.render("\b"))
        // U+0001 has no dedicated escape -> 
        assertEquals("\"\\u0001\"", JsonRenderer.render(""))
    }

    @Test
    fun `empty map renders inline`() {
        assertEquals("{}", JsonRenderer.render(emptyMap<String, Any>()))
    }

    @Test
    fun `empty list renders inline`() {
        assertEquals("[]", JsonRenderer.render(emptyList<Any>()))
    }

    @Test
    fun `simple map is pretty printed with two-space indent`() {
        val expected = "{\n  \"a\": 1,\n  \"b\": 2\n}"
        assertEquals(expected, JsonRenderer.render(linkedMapOf("a" to 1, "b" to 2)))
    }

    @Test
    fun `simple list is pretty printed with two-space indent`() {
        val expected = "[\n  1,\n  2\n]"
        assertEquals(expected, JsonRenderer.render(listOf(1, 2)))
    }

    @Test
    fun `nested list inside map indents one extra level`() {
        // {
        //   "nums": [
        //     1,
        //     2
        //   ]
        // }
        val value = linkedMapOf("nums" to listOf(1, 2))
        val expected = "{\n  \"nums\": [\n    1,\n    2\n  ]\n}"
        assertEquals(expected, JsonRenderer.render(value))
    }

    @Test
    fun `nested map inside list indents one extra level`() {
        // [
        //   {
        //     "k": "v"
        //   }
        // ]
        val value = listOf(linkedMapOf("k" to "v"))
        val expected = "[\n  {\n    \"k\": \"v\"\n  }\n]"
        assertEquals(expected, JsonRenderer.render(value))
    }

    @Test
    fun `deeply nested mixed structure indents consistently`() {
        // {
        //   "a": {
        //     "b": [
        //       1
        //     ]
        //   }
        // }
        val value = linkedMapOf("a" to linkedMapOf("b" to listOf(1)))
        val expected = "{\n  \"a\": {\n    \"b\": [\n      1\n    ]\n  }\n}"
        assertEquals(expected, JsonRenderer.render(value))
    }

    @Test
    fun `empty containers nested inside a map stay inline`() {
        val value = linkedMapOf("m" to emptyMap<String, Any>(), "l" to emptyList<Any>())
        val expected = "{\n  \"m\": {},\n  \"l\": []\n}"
        assertEquals(expected, JsonRenderer.render(value))
    }

    @Test
    fun `map with mixed value types`() {
        val value = linkedMapOf<String, Any?>("n" to 1, "s" to "hi", "b" to true, "nil" to null)
        val expected = "{\n  \"n\": 1,\n  \"s\": \"hi\",\n  \"b\": true,\n  \"nil\": null\n}"
        assertEquals(expected, JsonRenderer.render(value))
    }

    @Test
    fun `cyclic map is rejected instead of recursing forever`() {
        val value = linkedMapOf<String, Any?>()
        value["self"] = value

        assertThrows(IllegalArgumentException::class.java) {
            JsonRenderer.render(value)
        }
    }

    @Test
    fun `cyclic list is rejected instead of recursing forever`() {
        val value = ArrayList<Any?>()
        value.add(value)

        assertThrows(IllegalArgumentException::class.java) {
            JsonRenderer.render(value)
        }
    }

    @Test
    fun `NaN renders as null because JSON has no NaN`() {
        assertEquals("null", JsonRenderer.render(Double.NaN))
    }

    @Test
    fun `positive infinity renders as null`() {
        assertEquals("null", JsonRenderer.render(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `negative infinity renders as null`() {
        assertEquals("null", JsonRenderer.render(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `integral float renders without decimal point`() {
        assertEquals("24", JsonRenderer.render(24.0f))
    }

    @Test
    fun `fractional float keeps its value`() {
        assertEquals("1.5", JsonRenderer.render(1.5f))
    }

    @Test
    fun `map with non-String key uses the key's toString`() {
        val value = linkedMapOf<Any, Any>(1 to "a")
        assertEquals("{\n  \"1\": \"a\"\n}", JsonRenderer.render(value))
    }

    @Test
    fun `sibling reuse of the same object is not a cycle`() {
        // The same child appears twice as siblings (a DAG, not a cycle); rendering must not throw.
        val shared = linkedMapOf("v" to 1)
        val parent = linkedMapOf<String, Any>("x" to shared, "y" to shared)
        assertDoesNotThrow { JsonRenderer.render(parent) }
        assertEquals(
            "{\n  \"x\": {\n    \"v\": 1\n  },\n  \"y\": {\n    \"v\": 1\n  }\n}",
            JsonRenderer.render(parent),
        )
    }

    @Test
    fun `large integral double beyond 2^53 renders without wrong digits`() {
        // Beyond 2^53 a double can no longer hold every integer exactly, so the BigInteger
        // expansion would emit the binary value's spurious trailing digits. We fall back to the
        // standard double rendering, which is valid JSON and free of fabricated digits.
        assertEquals("1.0E21", JsonRenderer.render(1e21))
    }

    @Test
    fun `very large integral double renders without wrong digits`() {
        // Was "1000000000000000019884624838656" (the exact binary expansion) — wrong digits.
        assertEquals("1.0E30", JsonRenderer.render(1e30))
    }

    @Test
    fun `integral double at the 2^53 boundary still renders as a plain integer`() {
        // 2^53 (9007199254740992) is the largest magnitude where every integer is exactly
        // representable as a double, so it is rendered via the integer path (no decimal/exponent).
        assertEquals("9007199254740992", JsonRenderer.render(9007199254740992.0))
    }

    @Test
    fun `integral double around 1e15 still renders as a plain integer`() {
        assertEquals("1000000000000000", JsonRenderer.render(1e15))
    }

    @Test
    fun `BigInteger renders verbatim`() {
        assertEquals("100000000000000000000", JsonRenderer.render(BigInteger("100000000000000000000")))
    }

    @Test
    fun `BigDecimal renders via toString preserving scale`() {
        // BigDecimal is not normalized; its scale (here the trailing zero) is preserved as-is.
        assertEquals("1.50", JsonRenderer.render(BigDecimal("1.50")))
    }

    @Test
    fun `negative integral double at the 2^53 boundary renders as a plain integer`() {
        assertEquals("-9007199254740992", JsonRenderer.render(-9007199254740992.0))
    }

    @Test
    fun `negative large integral double beyond 2^53 falls back to double rendering`() {
        assertEquals("-1.0E30", JsonRenderer.render(-1e30))
    }

    @Test
    fun `negative zero renders as zero`() {
        assertEquals("0", JsonRenderer.render(-0.0))
    }

    @Test
    fun `unknown value type falls back to a quoted toString`() {
        // A Char is none of Map/List/CharSequence/Boolean/Number, so it hits the else fallback.
        assertEquals("\"x\"", JsonRenderer.render('x'))
    }

    @Test
    fun `nested NULL_VALUE inside a map renders as null`() {
        assertEquals("{\n  \"k\": null\n}", JsonRenderer.render(linkedMapOf("k" to Jsonata.NULL_VALUE)))
    }

    @Test
    fun `printable non-ascii characters pass through unescaped`() {
        assertEquals("\"café\"", JsonRenderer.render("café"))
    }
}
