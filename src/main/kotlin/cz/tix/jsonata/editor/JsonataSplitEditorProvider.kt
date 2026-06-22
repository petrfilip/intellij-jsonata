package cz.tix.jsonata.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import cz.tix.jsonata.isJsonFile

/**
 * Replaces the default editor for JSON files with a [JsonataSplitEditor] (text + JSONata preview).
 * `HIDE_DEFAULT_EDITOR` means each JSON file opens directly in the split editor, exactly like the
 * Markdown editor — so the playground always belongs to that one file.
 */
class JsonataSplitEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean = isJsonFile(file)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val preview = JsonataPreviewFileEditor(project, file, textEditor.editor.document)
        return JsonataSplitEditor(textEditor, preview)
    }

    override fun getEditorTypeId(): String = "jsonata-split-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
