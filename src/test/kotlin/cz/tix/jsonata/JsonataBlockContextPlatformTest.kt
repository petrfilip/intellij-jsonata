package cz.tix.jsonata

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import cz.tix.jsonata.completion.JsonataPlaygroundKeys

/**
 * IntelliJ Platform integration tests (JUnit3-style [BasePlatformTestCase]) covering the end-to-end
 * fix for the reported "block / predicate context offered root fields instead of the path's fields"
 * bug. They wire a realistic, nested-array document (`ORDERS`) through the document-bound JSON
 * supplier and drive the registered [cz.tix.jsonata.completion.JsonataCompletionContributor] via
 * [com.intellij.testFramework.fixtures.CodeInsightTestFixture.completeBasic].
 *
 * The `.jsonata` extension maps to the JSONata language through the `<fileType>` registration in
 * plugin.xml, so [com.intellij.testFramework.fixtures.CodeInsightTestFixture.configureByText]
 * produces a real [cz.tix.jsonata.lang.JsonataFile] with the completion contributor wired.
 *
 * Note on auto-insert: when exactly one lookup matches the prefix, the platform auto-inserts it and
 * `lookupElementStrings` is null. Inputs here are chosen to yield multiple matches so the lookup
 * list is observable; the single-match case asserts on the resulting document text instead.
 */
class JsonataBlockContextPlatformTest : BasePlatformTestCase() {

    private val ORDERS =
        "{\"Account\":{\"Name\":\"ACME\",\"Order\":[" +
            "{\"OrderID\":\"o1\",\"Product\":[" +
            "{\"Name\":\"A\",\"Price\":10,\"Quantity\":2}," +
            "{\"Name\":\"B\",\"Price\":5,\"Quantity\":4}]}," +
            "{\"OrderID\":\"o2\",\"Product\":[" +
            "{\"Name\":\"C\",\"Price\":3,\"Quantity\":1}]}]}}"

    /** Binds [ORDERS] as the document's JSON supplier (must run on the EDT, as it does here). */
    private fun bindOrders() {
        myFixture.editor.document.putUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) { ORDERS }
    }

    // ---- block context `path.( … )` offers the path's fields, not the root ----

    fun testBlockContextOffersFieldsOfPath() {
        // Inside `Product.( … )` the implicit context is each Product, so completion must offer the
        // product fields — and must NOT offer the root field 'Account'.
        myFixture.configureByText("t.jsonata", "\$sum(Account.Order.Product.(<caret>")
        bindOrders()
        myFixture.completeBasic()

        val lookups = myFixture.lookupElementStrings
        // VALUE mode also adds builtins + keywords, so the list always has >1 element (no auto-insert).
        assertNotNull("lookup list should be present (fields + functions + keywords)", lookups)
        assertTrue("expected product field 'Price' in $lookups", lookups!!.contains("Price"))
        assertTrue("expected product field 'Quantity' in $lookups", lookups.contains("Quantity"))
        assertTrue("expected product field 'Name' in $lookups", lookups.contains("Name"))
        assertFalse("root field 'Account' must NOT be offered in Product's context, got $lookups",
            lookups.contains("Account"))
    }

    // ---- block context with a partial token (single match -> auto-insert) ----

    fun testBlockContextWithPartial() {
        // Prefix "Pri" matches only the field 'Price' (no builtin/keyword starts with "Pri"), so the
        // platform auto-inserts it and lookupElementStrings is null. Assert on the document instead.
        myFixture.configureByText("t.jsonata", "\$sum(Account.Order.Product.(Pri<caret>")
        bindOrders()
        myFixture.completeBasic()

        val text = myFixture.editor.document.text
        assertTrue("expected 'Price' to be completed into the document, got: $text", text.contains("Price"))
    }

    // ---- predicate context `path[ … ]` offers the element's fields ----

    fun testPredicateContext() {
        // Inside `Product[ … ]` the predicate is evaluated against each Product element.
        myFixture.configureByText("t.jsonata", "Account.Order.Product[<caret>")
        bindOrders()
        myFixture.completeBasic()

        val lookups = myFixture.lookupElementStrings
        assertNotNull("lookup list should be present in a predicate", lookups)
        assertTrue("expected product field 'Price' in $lookups", lookups!!.contains("Price"))
        assertTrue("expected product field 'Quantity' in $lookups", lookups.contains("Quantity"))
    }

    // ---- root value position offers root fields ----

    fun testRootField() {
        myFixture.configureByText("t.jsonata", "<caret>")
        bindOrders()
        myFixture.completeBasic()

        val lookups = myFixture.lookupElementStrings
        assertNotNull("lookup list should be present (root fields + functions + keywords)", lookups)
        assertTrue("expected root field 'Account' in $lookups", lookups!!.contains("Account"))
    }

    // ---- field position after a `.` offers only fields ----

    fun testFieldAfterDot() {
        myFixture.configureByText("t.jsonata", "Account.<caret>")
        bindOrders()
        myFixture.completeBasic()

        val lookups = myFixture.lookupElementStrings
        assertNotNull("lookup list should be present for fields after a dot", lookups)
        assertTrue("expected field 'Name' in $lookups", lookups!!.contains("Name"))
        assertTrue("expected field 'Order' in $lookups", lookups.contains("Order"))
        // After a `.` only fields are offered — functions must be absent.
        assertFalse("\$sum must NOT be offered after a dot, got $lookups", lookups.contains("\$sum"))
    }
}
