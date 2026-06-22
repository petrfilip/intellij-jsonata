package cz.tix.jsonata.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonataEvaluatorTest {

    private val eval = JsonataEvaluator()
    private val json = """{"account":{"order":[{"price":10,"qty":2},{"price":5,"qty":4}]}}"""

    @Test
    fun `simple path returns scalar`() {
        val r = eval.evaluate(json, "account.order[0].price")
        assertTrue(r is JsonataEvaluator.Result.Success)
        assertEquals("10", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `aggregate over mapped array`() {
        val r = eval.evaluate(json, "\$sum(account.order.price)")
        assertEquals("15", (r as JsonataEvaluator.Result.Success).pretty)
    }

    @Test
    fun `object result is pretty printed`() {
        val r = eval.evaluate(json, "account.order[0]") as JsonataEvaluator.Result.Success
        assertTrue(r.pretty.contains("\"price\": 10"), "was: ${r.pretty}")
    }

    @Test
    fun `expression syntax error is reported as EXPRESSION phase`() {
        val r = eval.evaluate(json, "account.[")
        assertTrue(r is JsonataEvaluator.Result.Failure)
        assertEquals(JsonataEvaluator.Phase.EXPRESSION, (r as JsonataEvaluator.Result.Failure).phase)
    }

    @Test
    fun `invalid json is reported as JSON phase`() {
        val r = eval.evaluate("{not json", "account")
        assertTrue(r is JsonataEvaluator.Result.Failure)
        assertEquals(JsonataEvaluator.Phase.JSON, (r as JsonataEvaluator.Result.Failure).phase)
    }

    @Test
    fun `no match yields undefined note`() {
        val r = eval.evaluate(json, "account.missing") as JsonataEvaluator.Result.Success
        assertEquals("", r.pretty)
        assertEquals("no match (undefined)", r.note)
    }

    @Test
    fun `blank expression succeeds with empty output and no note`() {
        val r = eval.evaluate(json, "") as JsonataEvaluator.Result.Success
        assertEquals("", r.pretty)
        assertNull(r.note)
    }

    @Test
    fun `whitespace-only expression succeeds with empty output and no note`() {
        val r = eval.evaluate(json, "   ") as JsonataEvaluator.Result.Success
        assertEquals("", r.pretty)
        assertNull(r.note)
    }

    @Test
    fun `blank json with constant expression evaluates against no data`() {
        val r = eval.evaluate("", "1+1") as JsonataEvaluator.Result.Success
        assertEquals("2", r.pretty)
        assertNull(r.note)
    }

    @Test
    fun `explicit JSON null is treated as undefined, not as a null result`() {
        // The dashjoin engine parses JSON `null` as Java null (undefined), so selecting "a" yields
        // a no-match rather than the literal "null".
        val r = eval.evaluate("""{"a":null}""", "a") as JsonataEvaluator.Result.Success
        assertEquals("", r.pretty)
        assertEquals("no match (undefined)", r.note)
    }

    // Non-tail recursion ($loop returns its own depth): the trailing `+ 1` keeps the recursive call
    // out of tail position, so the engine cannot tail-call-optimize it and the stack actually grows.
    // ($loop(n) evaluates to n.)
    private val recursivePrelude = "\$loop := function(\$n){ \$n <= 0 ? 0 : \$loop(\$n - 1) + 1 };"

    @Test
    fun `recursion depth bound is enforced and reported as evaluation failure`() {
        // A tiny depth limit must abort deep recursion. Without the bound this returns 500 (Success),
        // so a Failure here proves setRuntimeBounds is wired and its two args aren't transposed.
        val bounded = JsonataEvaluator(maxRecursionDepth = 10)
        val r = bounded.evaluate("{}", "\$loop(500)", prelude = recursivePrelude)
        assertTrue(r is JsonataEvaluator.Result.Failure, "expected failure, was: $r")
        assertEquals(JsonataEvaluator.Phase.EVALUATION, (r as JsonataEvaluator.Result.Failure).phase)
    }

    @Test
    fun `recursion comfortably within the configured depth succeeds`() {
        // Same recursion, far under the default depth (500): guards against an over-tight bound.
        val r = eval.evaluate("{}", "\$loop(30)", prelude = recursivePrelude) as JsonataEvaluator.Result.Success
        assertEquals("30", r.pretty)
    }

    @Test
    fun `whitespace-only prelude is ignored like a blank prelude`() {
        // isBlank() (not isEmpty()) must govern both the validation skip and the source wrapping.
        val r = eval.evaluate("{}", "1+1", prelude = "   \n\t") as JsonataEvaluator.Result.Success
        assertEquals("2", r.pretty)
        assertNull(r.note)
    }
}
