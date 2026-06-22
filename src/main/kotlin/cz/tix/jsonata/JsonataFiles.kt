package cz.tix.jsonata

import com.intellij.openapi.vfs.VirtualFile

/** True for files we treat as JSON (the playground attaches to these). */
internal fun isJsonFile(file: VirtualFile): Boolean {
    val ext = file.extension?.lowercase()
    return ext == "json" || ext == "json5"
}
