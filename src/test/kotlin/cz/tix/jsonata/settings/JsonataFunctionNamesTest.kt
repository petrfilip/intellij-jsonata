package cz.tix.jsonata.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonataFunctionNamesTest {

    @Test
    fun `provider class names are trimmed and de-duplicated`() {
        val raw = listOf(" com.example.A ", "", "com.example.B", "com.example.A", "   ")

        assertEquals(
            listOf("com.example.A", "com.example.B"),
            JsonataFunctionNames.normalizeProviderClassNames(raw),
        )
    }

    @Test
    fun `function names accept optional leading dollar`() {
        assertEquals("greet", JsonataFunctionNames.normalizeFunctionName("greet"))
        assertEquals("greet", JsonataFunctionNames.normalizeFunctionName("\$greet"))
        assertEquals("_private1", JsonataFunctionNames.normalizeFunctionName("  \$_private1  "))
    }

    @Test
    fun `built-in function names are recognized without leading dollar`() {
        assertEquals(true, JsonataFunctionNames.isBuiltInFunctionName("sum"))
        assertEquals(false, JsonataFunctionNames.isBuiltInFunctionName("mySum"))
    }

    @Test
    fun `invalid function names are rejected`() {
        assertNull(JsonataFunctionNames.normalizeFunctionName(""))
        assertNull(JsonataFunctionNames.normalizeFunctionName("\$"))
        assertNull(JsonataFunctionNames.normalizeFunctionName("123bad"))
        assertNull(JsonataFunctionNames.normalizeFunctionName("bad-name"))
        assertNull(JsonataFunctionNames.normalizeFunctionName("bad.name"))
    }

    @Test
    fun `a dollar in the middle of a name is rejected`() {
        // Only a single leading '$' is stripped, so "a$b" still contains an illegal char.
        assertNull(JsonataFunctionNames.normalizeFunctionName("a\$b"))
    }

    @Test
    fun `a double leading dollar is rejected`() {
        // removePrefix strips only one '$', leaving "$x", which fails the identifier regex.
        assertNull(JsonataFunctionNames.normalizeFunctionName("\$\$x"))
    }

    @Test
    fun `provider class names preserve first-occurrence order`() {
        assertEquals(
            listOf("b", "a"),
            JsonataFunctionNames.normalizeProviderClassNames(listOf("b", "a", "b")),
        )
    }

    @Test
    fun `blank-only provider class entries are dropped`() {
        assertEquals(
            listOf("a"),
            JsonataFunctionNames.normalizeProviderClassNames(listOf("  ", "a", "", "a")),
        )
    }
}
