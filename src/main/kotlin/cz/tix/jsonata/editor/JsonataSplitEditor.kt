package cz.tix.jsonata.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview

/**
 * JSON editor on the left, JSONata playground preview on the right — same model as the Markdown
 * editor. Defaults to editor-only so normal JSON editing is undisturbed; the preview is revealed via
 * the split/preview toggle or the floating "Open JSONata Playground" button.
 */
class JsonataSplitEditor(editor: TextEditor, preview: FileEditor) :
    TextEditorWithPreview(editor, preview, "JSONata", Layout.SHOW_EDITOR)
