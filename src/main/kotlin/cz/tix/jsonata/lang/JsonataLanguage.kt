package cz.tix.jsonata.lang

import com.intellij.lang.Language

/** The JSONata [Language] singleton; [readResolve] keeps it a singleton across deserialization. */
object JsonataLanguage : Language("JSONata") {
    private fun readResolve(): Any = JsonataLanguage
    override fun getDisplayName(): String = "JSONata"
}
