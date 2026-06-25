package cz.tix.jsonata.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `mutating the store bumps the persistent state modification count`() {
        // Guards the removal of the (internal-API) manual intIncrementModificationCount() calls:
        // mutating a BaseState map() property must dirty the state on its own, or the IDE would
        // silently fail to persist remembered expressions across restarts.
        val store = JsonataExpressionStore()
        val initial = store.state.modificationCount

        store.put(fileA, "x.y")
        val afterPut = store.state.modificationCount
        assertTrue(afterPut > initial, "storing an expression must bump the modification count")

        store.put(fileA, "x.y") // no-op: same value, must not dirty the state
        assertEquals(afterPut, store.state.modificationCount)

        store.put(fileA, "") // removal must dirty the state again
        assertTrue(store.state.modificationCount > afterPut, "clearing an expression must bump the modification count")
    }
}
