package cz.tix.jsonata.completion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for [JsonataFieldKeys] driven by a realistic, nested-array document (the
 * `ORDERS` fixture). They pin the exact field names offered for the path contexts that power the
 * completion popup — including the array-union and engine-evaluated-predicate cases that a static
 * tree walk could not model — and the "no keys for a scalar / number / invalid input" guards.
 *
 * Depends only on the dashjoin engine; no IntelliJ runtime needed.
 *
 * Key ordering follows the parser's LinkedHashMap (JSON field order), so exact-list assertions are
 * used; the order is well-defined for this fixture.
 */
class JsonataFieldKeysContextTest {

    private val ORDERS = """
        {"Account":{"Name":"ACME","Order":[
            {"OrderID":"o1","Product":[
                {"Name":"A","Price":10,"Quantity":2},
                {"Name":"B","Price":5,"Quantity":4}]},
            {"OrderID":"o2","Product":[
                {"Name":"C","Price":3,"Quantity":1}]}]}}
    """.trimIndent()

    private fun keys(container: String?) = JsonataFieldKeys().keys(container, ORDERS)

    @Test
    fun `null container yields the root fields`() {
        assertEquals(listOf("Account"), keys(null))
    }

    @Test
    fun `Account yields its object fields`() {
        assertEquals(listOf("Name", "Order"), keys("Account"))
    }

    @Test
    fun `Account_Order yields the union of order fields`() {
        // Account.Order is an array of order objects; union of element keys.
        assertEquals(listOf("OrderID", "Product"), keys("Account.Order"))
    }

    @Test
    fun `Account_Order_Product yields product fields (powers the block-context completion)`() {
        // This is what powers `Product.(<caret>` completion: the flattened product array's keys.
        assertEquals(listOf("Name", "Price", "Quantity"), keys("Account.Order.Product"))
    }

    @Test
    fun `indexed Account_Order_0 yields the order fields`() {
        // Account.Order[0] selects the first order object -> its keys.
        assertEquals(listOf("OrderID", "Product"), keys("Account.Order[0]"))
    }

    @Test
    fun `predicate on products is evaluated by the engine`() {
        // Account.Order.Product[Price>4] keeps products A (10) and B (5); union of their keys.
        assertEquals(listOf("Name", "Price", "Quantity"), keys("Account.Order.Product[Price>4]"))
    }

    @Test
    fun `string leaf Product_Name has no keys`() {
        assertEquals(emptyList<String>(), keys("Account.Order.Product.Name"))
    }

    @Test
    fun `string leaf Account_Name has no keys`() {
        assertEquals(emptyList<String>(), keys("Account.Name"))
    }

    @Test
    fun `aggregate number result has no keys`() {
        // $sum(...) evaluates to a single number (18) -> not a Map/List -> no keys.
        assertEquals(emptyList<String>(), keys("\$sum(Account.Order.Product.Price)"))
    }

    // ---- fallback / invalid inputs ----------------------------------------

    @Test
    fun `invalid json yields no keys`() {
        assertEquals(emptyList<String>(), JsonataFieldKeys().keys("Account", "{not valid json"))
    }

    @Test
    fun `blank json yields no keys`() {
        assertEquals(emptyList<String>(), JsonataFieldKeys().keys(null, ""))
    }

    @Test
    fun `uncompilable container falls back to nothing`() {
        // "$bad(" never compiles -> evaluate() is null -> not a plain dotted path -> [].
        assertEquals(emptyList<String>(), keys("\$bad("))
    }

    @Test
    fun `dotted path into a missing segment yields no keys`() {
        // Account.Missing is a valid identifier path but resolves to nothing.
        assertEquals(emptyList<String>(), keys("Account.Missing"))
    }

    @Test
    fun `array-union keys contain the product fields regardless of order`() {
        // Order-independent restatement of the block-context case, for robustness.
        val result = keys("Account.Order.Product")
        assertTrue(result.containsAll(listOf("Name", "Price", "Quantity")), "was: $result")
        assertEquals(3, result.size, "no duplicate keys expected, was: $result")
    }

    // ---- indexed-then-product / empty predicate / aggregate leaf -----------

    @Test
    fun `indexed order then product yields the product fields`() {
        // Account.Order[0] selects the first order; ".Product" then flattens its product array.
        assertEquals(listOf("Name", "Price", "Quantity"), keys("Account.Order[0].Product"))
    }

    @Test
    fun `predicate that matches nothing yields no keys`() {
        // No product has Price > 999, so the predicate matches nothing -> no keys.
        assertEquals(emptyList<String>(), keys("Account.Order.Product[Price>999]"))
    }

    @Test
    fun `count aggregate leaf has no keys`() {
        // $count(...) evaluates to a single number -> not a Map/List -> no keys.
        assertEquals(emptyList<String>(), keys("\$count(Account.Order)"))
    }
}
