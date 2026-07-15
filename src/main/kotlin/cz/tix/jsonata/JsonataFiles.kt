package cz.tix.jsonata

import com.intellij.openapi.vfs.VirtualFile

/**
 * True for strict JSON files supported by the bundled JSONata engine.
 *
 * JSON5 is intentionally excluded: accepting it here would replace the IDE editor and then fail at
 * evaluation time because the engine input parser only accepts standard JSON.
 */
internal fun isJsonFile(file: VirtualFile): Boolean =
    file.extension.equals("json", ignoreCase = true)
