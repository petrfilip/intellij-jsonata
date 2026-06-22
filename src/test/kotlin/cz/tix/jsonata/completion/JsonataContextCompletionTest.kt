package cz.tix.jsonata.completion

import cz.tix.jsonata.completion.JsonataExpressionScanner.Ctx
import cz.tix.jsonata.completion.JsonataExpressionScanner.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Focused tests for the implicit block/predicate context resolution in
 * [JsonataExpressionScanner.analyze]. Each expected (mode, container) below was derived by
 * simulating `analyze`, `blockContextAt`, `innermostOpener`, `extractContainer`, `combine` and
 * `stringMask` by hand against the source.
 *
 * These intentionally do NOT repeat the trivial cases already covered by
 * JsonataExpressionScannerTest (e.g. "", "account", "account.", "$su", "$sum(",
 * "$sum(account.order.pr"); they exercise the newer block/predicate composition instead.
 */
class JsonataContextCompletionTest {

    private fun analyzeEnd(text: String): Ctx =
        JsonataExpressionScanner.analyze(text, text.length)

    private fun assertCtx(expectedMode: Mode, expectedContainer: String?, actual: Ctx) {
        assertEquals(expectedMode, actual.mode, "mode for $actual")
        assertEquals(expectedContainer, actual.containerExpr, "container for $actual")
    }

    // ---- the reported bug: block context after a function-call path ---------

    @Test
    fun `block opener right after a path inside a call exposes the path as VALUE container`() {
        // The reported bug: "$sum(Account.Order.Product.(" should offer fields of
        // Account.Order.Product, not the JSON root.
        assertCtx(Mode.VALUE, "Account.Order.Product", analyzeEnd("\$sum(Account.Order.Product.("))
    }

    @Test
    fun `partial expression inside the block keeps the path container in VALUE position`() {
        // "...(Price * Q" -> the significant char before "Q" is '*' (operator) -> VALUE,
        // and the block context is still the path before the block opener.
        assertCtx(Mode.VALUE, "Account.Order.Product", analyzeEnd("\$sum(Account.Order.Product.(Price * Q"))
    }

    @Test
    fun `field after a dot inside a block combines block context with local chain`() {
        // "Account.Order.Product.(Price.su" -> FIELD, container = block path + local "Price".
        assertCtx(Mode.FIELD, "Account.Order.Product.Price", analyzeEnd("Account.Order.Product.(Price.su"))
    }

    // ---- top-level block: EXPR.( ... ) -> context is EXPR -------------------

    @Test
    fun `empty block after a path is VALUE with the path as container`() {
        assertCtx(Mode.VALUE, "Account.Order", analyzeEnd("Account.Order.("))
    }

    @Test
    fun `value after a binary operator inside a block keeps the block container`() {
        assertCtx(Mode.VALUE, "Account.Order", analyzeEnd("Account.Order.(Price + "))
    }

    // ---- nested blocks compose ---------------------------------------------

    @Test
    fun `nested empty blocks compose their path containers`() {
        // "a.(b.(c." -> FIELD, container = a.b.c (outer block 'a', inner block 'b', local 'c').
        assertCtx(Mode.FIELD, "a.b.c", analyzeEnd("a.(b.(c."))
    }

    @Test
    fun `nested blocks with a partial field still compose to the same container`() {
        // "a.(b.(c.d" -> partial "d" after the dot; container is the chain before the dot.
        assertCtx(Mode.FIELD, "a.b.c", analyzeEnd("a.(b.(c.d"))
    }

    // ---- predicates: EXPR[ ... ] -> context is EXPR -------------------------

    @Test
    fun `field after a predicate offers the predicated expression`() {
        assertCtx(Mode.FIELD, "Account[id=1]", analyzeEnd("Account[id=1]."))
    }

    @Test
    fun `caret directly inside an empty predicate is VALUE on the element type`() {
        // "Account[" -> caret inside the predicate; element fields of Account are valid.
        assertCtx(Mode.VALUE, "Account", analyzeEnd("Account["))
    }

    @Test
    fun `partial token inside a predicate is VALUE on the element type`() {
        assertCtx(Mode.VALUE, "Account", analyzeEnd("Account[pri"))
    }

    @Test
    fun `field after a multi-step predicated path keeps the whole predicate`() {
        assertCtx(Mode.FIELD, "Account.Order[Price>5]", analyzeEnd("Account.Order[Price>5]."))
    }

    // ---- block context with variable binding (':=') ------------------------

    @Test
    fun `value position after a binding inside a block uses the block container`() {
        // "items.($x := pr" -> partial "pr", significant char before is '=' -> VALUE;
        // block context is "items".
        assertCtx(Mode.VALUE, "items", analyzeEnd("items.(\$x := pr"))
    }

    // ---- whitespace handling around dots and openers -----------------------

    @Test
    fun `whitespace inside a block path container is preserved verbatim`() {
        // The scanner only trims the OUTER ends of the extracted container; interior
        // whitespace ("Account . Order") is kept. See the report note on this.
        assertCtx(Mode.VALUE, "Account . Order", analyzeEnd("Account . Order . ("))
    }

    // ---- my own additional cases -------------------------------------------

    @Test
    fun `chained predicate then block uses the predicated path as block container`() {
        // "Account.Order[Price>5].(" -> VALUE, container = the whole predicated path.
        assertCtx(Mode.VALUE, "Account.Order[Price>5]", analyzeEnd("Account.Order[Price>5].("))
    }

    @Test
    fun `predicate inside a block composes block and local path as the element container`() {
        // "items.(values[" -> caret inside predicate; container = "items" (block) + "values".
        assertCtx(Mode.VALUE, "items.values", analyzeEnd("items.(values["))
    }

    @Test
    fun `field after a predicate that lives inside a block composes everything`() {
        // "items.(values[type='x']." -> FIELD; the string literal in the predicate is opaque.
        assertCtx(Mode.FIELD, "items.values[type='x']", analyzeEnd("items.(values[type='x']."))
    }

    @Test
    fun `object constructor after a path exposes the path container`() {
        // "orders.{" -> '{' preceded by '.' is a constructor mapped over each item of orders,
        // so fields of orders are valid in the constructor.
        assertCtx(Mode.VALUE, "orders", analyzeEnd("orders.{"))
    }

    @Test
    fun `value position inside an object constructor after a path uses the path container`() {
        // "orders.{name: " -> still the orders context.
        assertCtx(Mode.VALUE, "orders", analyzeEnd("orders.{name: "))
    }

    @Test
    fun `block context propagates through a nested plain paren`() {
        // "items.(($x" -> partial "$x" -> FUNCTION (dollar token wins before any context work).
        assertCtx(Mode.FUNCTION, null, analyzeEnd("items.((\$x"))
    }

    @Test
    fun `value inside a nested plain paren within a block keeps the block container`() {
        // "items.((pr" -> partial "pr", before it '(' -> VALUE; the plain inner paren
        // delegates to the block context "items".
        assertCtx(Mode.VALUE, "items", analyzeEnd("items.((pr"))
    }

    @Test
    fun `field after a nested plain paren within a block composes block plus local chain`() {
        // "items.((order.pr" -> FIELD; block "items" + local "order".
        assertCtx(Mode.FIELD, "items.order", analyzeEnd("items.((order.pr"))
    }
}
