package cz.tix.jsonata.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

/** PSI file root for the JSONata language. */
class JsonataFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, JsonataLanguage) {
    override fun getFileType(): FileType = JsonataFileType
    override fun toString(): String = "JSONata File"
}
