package cz.tix.jsonata.completion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Integrity tests for the [JsonataBuiltins] catalogue. */
class JsonataBuiltinsTest {

    @Test
    fun `functions list is not empty`() {
        assertTrue(JsonataBuiltins.FUNCTIONS.isNotEmpty())
    }

    @Test
    fun `every function name starts with dollar`() {
        JsonataBuiltins.FUNCTIONS.forEach { fn ->
            assertTrue(fn.name.startsWith("$"), "name does not start with \$: ${fn.name}")
        }
    }

    @Test
    fun `function names are unique`() {
        val names = JsonataBuiltins.FUNCTIONS.map { it.name }
        val duplicates = names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate function names: $duplicates")
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `byName returns the same Fn for every function`() {
        JsonataBuiltins.FUNCTIONS.forEach { fn ->
            assertSame(fn, JsonataBuiltins.byName(fn.name), "byName mismatch for ${fn.name}")
        }
    }

    @Test
    fun `byName of unknown is null`() {
        assertNull(JsonataBuiltins.byName("\$nope"))
    }

    @Test
    fun `signature starts with the name and is parenthesized`() {
        JsonataBuiltins.FUNCTIONS.forEach { fn ->
            val sig = fn.signature
            assertTrue(sig.startsWith(fn.name + "("), "signature should start with '${fn.name}(': $sig")
            assertTrue(sig.endsWith(")"), "signature should end with ')': $sig")
        }
    }

    @Test
    fun `optional params render in brackets and required params do not`() {
        JsonataBuiltins.FUNCTIONS.forEach { fn ->
            fn.params.forEach { p ->
                if (p.optional) {
                    assertEquals("[${p.name}]", p.display(), "optional param should be bracketed: ${fn.name}")
                } else {
                    assertEquals(p.name, p.display(), "required param should be bare: ${fn.name}")
                }
            }
        }
        // Concrete example: $substring(str, start, [length])
        val substring = JsonataBuiltins.byName("\$substring")!!
        assertEquals("\$substring(str, start, [length])", substring.signature)
    }

    @Test
    fun `sum has a single required param`() {
        val sum = JsonataBuiltins.byName("\$sum")
        assertNotNull(sum)
        sum!!
        assertEquals(1, sum.params.size)
        assertEquals(1, sum.params.count { !it.optional })
        assertTrue(sum.params.none { it.optional })
    }

    @Test
    fun `substring has three params with optional length`() {
        val substring = JsonataBuiltins.byName("\$substring")
        assertNotNull(substring)
        substring!!
        assertEquals(3, substring.params.size)
        assertEquals(listOf("str", "start", "length"), substring.params.map { it.name })
        assertTrue(!substring.params[0].optional, "str must be required")
        assertTrue(!substring.params[1].optional, "start must be required")
        assertTrue(substring.params[2].optional, "length must be optional")
    }

    @Test
    fun `now has only optional params`() {
        val now = JsonataBuiltins.byName("\$now")
        assertNotNull(now)
        now!!
        assertTrue(now.params.isNotEmpty(), "expected \$now to declare params")
        assertTrue(now.params.all { it.optional }, "all \$now params should be optional")
        assertEquals("\$now([picture], [timezone])", now.signature)
    }

    @Test
    fun `keywords contain the expected entries`() {
        val expected = listOf("and", "or", "in", "true", "false", "null")
        expected.forEach { kw ->
            assertTrue(JsonataBuiltins.KEYWORDS.contains(kw), "KEYWORDS missing: $kw")
        }
    }

    @Test
    fun `no-arg functions render an empty parameter list`() {
        val random = JsonataBuiltins.byName("\$random")
        assertNotNull(random)
        random!!
        assertTrue(random.params.isEmpty(), "expected \$random to declare no params")
        assertEquals("\$random()", random.signature)

        val millis = JsonataBuiltins.byName("\$millis")
        assertNotNull(millis)
        millis!!
        assertTrue(millis.params.isEmpty(), "expected \$millis to declare no params")
        assertEquals("\$millis()", millis.signature)
    }

    @Test
    fun `every function has a non-blank return type and summary`() {
        JsonataBuiltins.FUNCTIONS.forEach { fn ->
            assertTrue(fn.returns.isNotBlank(), "blank return type for ${fn.name}")
            assertTrue(fn.summary.isNotBlank(), "blank summary for ${fn.name}")
        }
    }

    @Test
    fun `keywords contain function`() {
        assertTrue(JsonataBuiltins.KEYWORDS.contains("function"), "KEYWORDS missing: function")
    }

    @Test
    fun `keywords have no duplicates`() {
        val keywords = JsonataBuiltins.KEYWORDS
        assertEquals(keywords.size, keywords.toSet().size, "duplicate keywords in: $keywords")
    }

    // ---- Param.display() ---------------------------------------------------

    @Test
    fun `optional Param renders bracketed and required Param renders bare`() {
        assertEquals("[x]", JsonataBuiltins.Param("x", optional = true).display())
        assertEquals("x", JsonataBuiltins.Param("x", optional = false).display())
        // optional defaults to false.
        assertEquals("x", JsonataBuiltins.Param("x").display())
    }

    @Test
    fun `zip signature renders its variadic optional param in brackets`() {
        // $zip declares a trailing optional "..." param, which must render as "[...]".
        assertEquals("\$zip(array1, [...])", JsonataBuiltins.byName("\$zip")!!.signature)
    }

    // ---- byName lookup edges -----------------------------------------------

    @Test
    fun `byName without the dollar prefix is null`() {
        // Catalogue keys include the leading '$', so the bare name does not resolve.
        assertNull(JsonataBuiltins.byName("sum"))
    }

    @Test
    fun `byName is case-sensitive`() {
        assertNull(JsonataBuiltins.byName("\$SUM"))
    }

    @Test
    fun `every function name resolves via byName and names are unique`() {
        JsonataBuiltins.FUNCTIONS.forEach { fn ->
            assertSame(fn, JsonataBuiltins.byName(fn.name), "byName must resolve ${fn.name}")
        }
        // A 1:1 name<->Fn map: distinct names == catalogue size.
        val names = JsonataBuiltins.FUNCTIONS.map { it.name }
        assertEquals(names.size, names.toSet().size, "duplicate function names")
    }

    // ---- keywords exact ordered list ---------------------------------------

    @Test
    fun `keywords are exactly the expected ordered list`() {
        assertEquals(listOf("and", "or", "in", "function", "true", "false", "null"), JsonataBuiltins.KEYWORDS)
    }
}
