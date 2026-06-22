package cz.tix.jsonata.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/** [LanguageFileType] for `.jsonata` files. */
object JsonataFileType : LanguageFileType(JsonataLanguage) {
    override fun getName(): String = "JSONata"
    override fun getDescription(): String = "JSONata expression"
    override fun getDefaultExtension(): String = "jsonata"
    override fun getIcon(): Icon = JsonataIcons.FILE
}
