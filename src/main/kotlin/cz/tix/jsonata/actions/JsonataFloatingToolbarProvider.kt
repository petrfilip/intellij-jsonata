package cz.tix.jsonata.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import cz.tix.jsonata.isJsonFile

/**
 * Shows a small floating button in the top-right of the editor when a JSON file is open, so the
 * playground can be opened with one click instead of via the context menu. The button hosts the
 * `Jsonata.FloatingToolbar` action group (registered in plugin.xml).
 */
class JsonataFloatingToolbarProvider : AbstractFloatingToolbarProvider("Jsonata.FloatingToolbar") {

    // Keep it visible (don't fade out on idle) while a JSON file is open.
    override val autoHideable: Boolean get() = false

    override fun isApplicable(dataContext: DataContext): Boolean {
        val file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false
        return isJsonFile(file)
    }
}
