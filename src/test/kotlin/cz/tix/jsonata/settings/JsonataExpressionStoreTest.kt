package cz.tix.jsonata.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonataExpressionStoreTest {

    private val fileA = "file:///project/a.json"
    private val fileB = "file:///project/b.json"

    @Test
    fun `remembers an expression per file url`() {
        val store = JsonataExpressionStore()

        store.put(fileA, "account.order.product")
        store.put(fileB, "\$sum(items.price)")

        assertEquals("account.order.product", store.get(fileA))
        assertEquals("\$sum(items.price)", store.get(fileB))
    }

    @Test
    fun `unknown file has no remembered expression`() {
        assertNull(JsonataExpressionStore().get(fileA))
    }

    @Test
    fun `a later expression overwrites the earlier one`() {
        val store = JsonataExpressionStore()

        store.put(fileA, "first")
        store.put(fileA, "second")

        assertEquals("second", store.get(fileA))
    }

    @Test
    fun `clearing the expression drops the entry`() {
        val store = JsonataExpressionStore()

        store.put(fileA, "x.y")
        store.put(fileA, "")

        assertNull(store.get(fileA))
    }
}
