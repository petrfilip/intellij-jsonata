package cz.tix.jsonata.completion

import com.intellij.openapi.util.Key

/** Keys shared between the playground panel and the language-level extensions. */
object JsonataPlaygroundKeys {
    /** Supplier of the bound JSON text, stashed on the expression editor's document. */
    val JSON_SUPPLIER: Key<() -> String> = Key.create("jsonata.playground.jsonSupplier")
}
