package cz.tix.jsonata.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import cz.tix.jsonata.toolwindow.JsonataPlaygroundPanel
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * The "preview" side of the JSONata split editor: hosts a [JsonataPlaygroundPanel] bound to this
 * file's document, so the playground is intrinsically per-file (like the Markdown preview).
 */
class JsonataPreviewFileEditor(
    project: Project,
    private val file: VirtualFile,
    document: Document,
) : UserDataHolderBase(), FileEditor {

    private val panel = JsonataPlaygroundPanel(project, this).apply { bindSource(document, file) }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel.preferredFocusComponent()
    override fun getName(): String = "JSONata Playground"
    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

    // The panel is registered as a child Disposable of this editor, so it is released automatically.
    override fun dispose() {}
}
