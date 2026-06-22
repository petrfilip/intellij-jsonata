package cz.tix.jsonata.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import cz.tix.jsonata.isJsonFile

/**
 * Opens the JSON file (if needed) and reveals the JSONata playground by switching its split editor
 * to the editor+preview layout. Invoked from the editor floating button and the project-view popup.
 */
class OpenJsonataPlaygroundAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null && file != null && isJsonFile(file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editors = FileEditorManager.getInstance(project).openFile(file, true)
        editors.filterIsInstance<TextEditorWithPreview>().firstOrNull()
            ?.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
    }
}
