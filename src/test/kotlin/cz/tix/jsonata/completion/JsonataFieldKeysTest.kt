package cz.tix.jsonata.completion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [JsonataFieldKeys] — exercises both the "evaluate the prefix" path and the
 * static dotted-path fallback. Depends only on the dashjoin engine; no IntelliJ runtime needed.
 *
 * Key ordering follows the parser's LinkedHashMap (JSON field order), so exact-list assertions are
 * used where the order is well-defined.
 */
class JsonataFieldKeysTest {

    private val obj = """{"a":{"b":1,"c":2}}"""

    @Test
    fun `root keys of an object`() {
        val keys = JsonataFieldKeys()
        assertEquals(listOf("a"), keys.keys(null, obj))
    }

    @Test
    fun `nested object keys via container`() {
        val keys = JsonataFieldKeys()
        assertEquals(listOf("b", "c"), keys.keys("a", obj))
    }

    @Test
    fun `blank container behaves like root`() {
        val keys = JsonataFieldKeys()
        assertEquals(listOf("a"), keys.keys("   ", obj))
    }

    @Test
    fun `scalar leaf has no keys`() {
        val keys = JsonataFieldKeys()
        // a.b is the number 1 -> not a Map/List -> no keys
        assertEquals(emptyList<String>(), keys.keys("a.b", obj))
    }

    @Test
    fun `array union collects keys from all elements`() {
        val keys = JsonataFieldKeys()
        val json = """{"items":[{"x":1},{"y":2}]}"""
        val result = keys.keys("items", json)
        assertTrue(result.containsAll(listOf("x", "y")), "was: $result")
        assertEquals(2, result.size, "no dupes expected, was: $result")
    }

    @Test
    fun `predicate selects a single object and yields its keys`() {
        val keys = JsonataFieldKeys()
        val json = """{"items":[{"x":1,"z":3},{"x":2}]}"""
        // items[x=1] evaluates to the single matching object {x:1, z:3}
        assertEquals(listOf("x", "z"), keys.keys("items[x=1]", json))
    }

    @Test
    fun `root array unions keys of its objects`() {
        val keys = JsonataFieldKeys()
        val json = """[{"a":1,"b":2}]"""
        assertEquals(listOf("a", "b"), keys.keys(null, json))
    }

    @Test
    fun `invalid json yields no keys for root`() {
        val keys = JsonataFieldKeys()
        assertEquals(emptyList<String>(), keys.keys(null, "{nope"))
    }

    @Test
    fun `invalid json yields no keys for a container`() {
        val keys = JsonataFieldKeys()
        assertEquals(emptyList<String>(), keys.keys("a", "{nope"))
    }

    @Test
    fun `blank json yields no keys`() {
        val keys = JsonataFieldKeys()
        assertEquals(emptyList<String>(), keys.keys(null, ""))
    }

    @Test
    fun `unevaluable non-path container yields no keys`() {
        val keys = JsonataFieldKeys()
        // "$bogus(" fails to compile -> evaluate() returns null -> not a plain dotted path -> []
        assertEquals(emptyList<String>(), keys.keys("\$bogus(", obj))
    }

    @Test
    fun `dotted-path fallback walks into a missing segment as no keys`() {
        val keys = JsonataFieldKeys()
        // "a.missing" is a valid identifier path but resolves to nothing -> []
        assertEquals(emptyList<String>(), keys.keys("a.missing", obj))
    }

    @Test
    fun `repeated calls return stable results (caching)`() {
        val keys = JsonataFieldKeys()
        val first = keys.keys(null, obj)
        val second = keys.keys(null, obj)
        assertEquals(first, second)
        assertEquals(listOf("a"), second)
    }

    @Test
    fun `cache survives interleaved different containers on same json`() {
        val keys = JsonataFieldKeys()
        // Same json text re-used across calls -> cached parse is reused but results differ per container.
        assertEquals(listOf("a"), keys.keys(null, obj))
        assertEquals(listOf("b", "c"), keys.keys("a", obj))
        assertEquals(listOf("a"), keys.keys(null, obj))
    }

    @Test
    fun `cache tracks changes when switching json documents`() {
        val keys = JsonataFieldKeys()
        assertEquals(listOf("a"), keys.keys(null, """{"a":1}"""))
        assertEquals(listOf("b"), keys.keys(null, """{"b":1}"""))
        assertEquals(listOf("a"), keys.keys(null, """{"a":1}"""))
    }

    @Test
    fun `keysOf returns empty for non-container values`() {
        assertEquals(emptyList<String>(), JsonataFieldKeys.keysOf(42))
        assertEquals(emptyList<String>(), JsonataFieldKeys.keysOf("text"))
        assertEquals(emptyList<String>(), JsonataFieldKeys.keysOf(null))
    }

    @Test
    fun `keysOf de-duplicates across a list of maps`() {
        val list = listOf(linkedMapOf("x" to 1, "y" to 2), linkedMapOf("x" to 3, "z" to 4))
        assertEquals(listOf("x", "y", "z"), JsonataFieldKeys.keysOf(list))
    }

    // ---- cache-of-failure / scalar roots / mixed lists / empty object ------

    @Test
    fun `repeated invalid json stays empty and is not re-parsed`() {
        val keys = JsonataFieldKeys()
        // First call caches the (null) parse-of-failure for this text; the second reuses it.
        assertEquals(emptyList<String>(), keys.keys(null, "{nope"))
        assertEquals(emptyList<String>(), keys.keys("a", "{nope"))
        assertEquals(emptyList<String>(), keys.keys(null, "{nope"))
    }

    @Test
    fun `scalar number root json has no keys`() {
        assertEquals(emptyList<String>(), JsonataFieldKeys().keys(null, "42"))
    }

    @Test
    fun `scalar string root json has no keys`() {
        assertEquals(emptyList<String>(), JsonataFieldKeys().keys(null, "\"hi\""))
    }

    @Test
    fun `keysOf over a mixed list ignores non-map elements and preserves first-seen order`() {
        val mixed = listOf(linkedMapOf("a" to 1, "b" to 2), "scalar", 42, linkedMapOf("c" to 3, "a" to 4))
        assertEquals(listOf("a", "b", "c"), JsonataFieldKeys.keysOf(mixed))
    }

    @Test
    fun `empty object has no keys`() {
        assertEquals(emptyList<String>(), JsonataFieldKeys().keys(null, "{}"))
    }

    @Test
    fun `shared instance returns correct keys across many sequential mixed calls`() {
        // Regression guard for the shared-instance thread-safety fix: `keys` is the single
        // `@Synchronized` entry point, so the cache read and the engine evaluation it feeds run
        // under one monitor. A true race is non-deterministic to test, so this asserts that the
        // shared instance stays correct under repeated, interleaved container/document calls.
        val keys = JsonataFieldKeys()
        val docA = """{"a":{"b":1,"c":2}}"""
        val docB = """{"x":[{"p":1},{"q":2}]}"""
        repeat(50) {
            assertEquals(listOf("a"), keys.keys(null, docA))
            assertEquals(listOf("b", "c"), keys.keys("a", docA))
            assertEquals(listOf("p", "q"), keys.keys("x", docB))
            assertEquals(listOf("x"), keys.keys(null, docB))
        }
    }

    // ---- runtime bound -----------------------------------------------------

    @Test
    fun `tight timeout bound returns quickly and empty for an expensive container`() {
        // A million-element reduce: with the default bound it runs for hundreds of ms, but a 1ms
        // runtime bound truncates it. Either way the result is a number (no keys), but the bound is
        // what makes it return *quickly* rather than completing the whole fold.
        val expensive = "\$reduce([1..1000000], function(\$a, \$b){ \$a + \$b })"
        val keys = JsonataFieldKeys(evalTimeoutMillis = 1L)
        val start = System.nanoTime()
        val result = keys.keys(expensive, "{}")
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(emptyList<String>(), result)
        // Generously loose ceiling: the tight bound reliably finishes in ~1ms; 1s leaves ample slack.
        assertTrue(elapsedMs < 1000, "tight runtime bound should return quickly, took ${elapsedMs}ms")
    }
}
