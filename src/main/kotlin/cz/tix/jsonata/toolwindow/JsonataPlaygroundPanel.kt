package cz.tix.jsonata.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.LanguageTextField
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import cz.tix.jsonata.CUSTOM_FUNCTIONS_ENABLED
import cz.tix.jsonata.completion.JsonataPlaygroundKeys
import cz.tix.jsonata.engine.JsonataEvaluator
import cz.tix.jsonata.lang.JsonataLanguage
import cz.tix.jsonata.settings.JSONATA_SETTINGS_TOPIC
import cz.tix.jsonata.settings.JsonataExpressionStore
import cz.tix.jsonata.settings.JsonataFunctionLoader
import cz.tix.jsonata.settings.JsonataProjectSettings
import cz.tix.jsonata.settings.JsonataSettingsListener
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The playground UI hosted in the "JSONata" tool window: a JSONata expression editor (with
 * completion) on top and a read-only JSON result viewer below. It is bound to a source JSON
 * [Document] (the file the user opened the playground from) and re-evaluates, debounced, whenever
 * either the JSON or the expression changes.
 */
class JsonataPlaygroundPanel(
    private val project: Project,
    parentDisposable: Disposable,
) : JPanel(BorderLayout()), Disposable {

    private val evaluator = JsonataEvaluator()
    private val editorFactory = EditorFactory.getInstance()

    private val expressionField = LanguageTextField(JsonataLanguage, project, "", false)

    private val resultDocument: Document = editorFactory.createDocument("")
    private val resultEditor: EditorEx = editorFactory.createViewer(resultDocument, project) as EditorEx

    // Elastic labels never force the split editor wider — long text (e.g. a JSONata error) is clipped
    // with an ellipsis and surfaced in full via the tooltip instead. See [ElasticLabel].
    private val sourceLabel = ElasticLabel(NO_SOURCE_TEXT)
    private val functionsLabel = ElasticLabel(" ")
    private val statusLabel = ElasticLabel(" ")

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)

    private var sourceDocument: Document? = null
    private var sourceListener: DocumentListener? = null
    // URL of the bound JSON file; the key under which its expression is remembered (null = in-memory).
    private var sourceFileUrl: String? = null
    private val disposed = AtomicBoolean()
    private val evaluationSeq = AtomicLong()

    init {
        Disposer.register(parentDisposable, this)

        configureExpressionField()
        configureResultEditor()

        sourceLabel.border = JBUI.Borders.empty(4, 6)

        functionsLabel.border = JBUI.Borders.empty(0, 6, 4, 6)
        functionsLabel.foreground = JBColor.GRAY

        val splitter = OnePixelSplitter(true, 0.32f).apply {
            firstComponent = section("JSONata expression", expressionField)
            secondComponent = section("Result", resultEditor.component, statusLabel)
        }

        val header = JPanel(GridLayout(0, 1)).apply {
            add(sourceLabel)
            add(functionsLabel)
        }
        add(header, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)

        expressionField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                persistExpression()
                scheduleEvaluation()
            }
        }, this)

        // Reload custom functions and re-evaluate when the project settings change.
        project.messageBus.connect(this).subscribe(JSONATA_SETTINGS_TOPIC, JsonataSettingsListener {
            JsonataFunctionLoader.getInstance(project).invalidate()
            scheduleEvaluation()
        })
    }

    /** Binds the playground to a JSON document and starts live evaluation against it. */
    fun bindSource(document: Document, file: VirtualFile?) {
        sourceListener?.let { old -> sourceDocument?.removeDocumentListener(old) }
        sourceDocument = document
        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleEvaluation()
        }
        // Registered WITHOUT a parent disposable on purpose: the source document can be rebound (the
        // removal just above) and is removed manually here and in dispose(). Passing `this` as the
        // parent would make the platform auto-remove it on dispose too, double-removing the listener
        // and logging "Can't remove document listener".
        document.addDocumentListener(listener)
        sourceListener = listener
        sourceFileUrl = file?.url
        sourceLabel.text = "Source JSON: ${file?.name ?: "(in-memory document)"}"
        restoreExpression()
        scheduleEvaluation()
    }

    /** Restores the expression last remembered for the bound file (no-op for in-memory documents). */
    private fun restoreExpression() {
        val saved = sourceFileUrl?.let { JsonataExpressionStore.getInstance(project).get(it) } ?: return
        if (saved != expressionField.text) expressionField.text = saved
    }

    /** Persists the current expression for the bound file so it survives reopen and IDE restart. */
    private fun persistExpression() {
        val url = sourceFileUrl ?: return
        JsonataExpressionStore.getInstance(project).put(url, expressionField.text)
    }

    private fun scheduleEvaluation() {
        if (disposed.get()) return
        val requestId = evaluationSeq.incrementAndGet()
        alarm.cancelAllRequests()
        alarm.addRequest({ evaluate(requestId) }, DEBOUNCE_MS)
    }

    /**
     * Threading contract: read text + settings on the EDT, then load functions and evaluate on a
     * pooled (off-EDT) thread, and apply the result back on the EDT. A stale run is discarded whenever
     * [requestId] != [evaluationSeq] (a newer keystroke has superseded it).
     */
    private fun evaluate(requestId: Long) {
        if (project.isDisposed || disposed.get()) return
        // Read text + settings on the EDT, then load functions and evaluate off-EDT.
        val jsonText = sourceDocument?.immutableCharSequence?.toString() ?: ""
        val expression = expressionField.text
        // Custom functions (prelude + JVM providers) are gated behind CUSTOM_FUNCTIONS_ENABLED.
        // While off, evaluate the bare expression only — no prelude, no providers, no status row.
        val prelude = if (CUSTOM_FUNCTIONS_ENABLED) JsonataProjectSettings.getInstance(project).state.prelude ?: "" else ""
        ApplicationManager.getApplication().executeOnPooledThread {
            if (disposed.get() || project.isDisposed || requestId != evaluationSeq.get()) return@executeOnPooledThread
            val loaded = if (CUSTOM_FUNCTIONS_ENABLED) JsonataFunctionLoader.getInstance(project).load() else null
            val result = evaluator.evaluate(jsonText, expression, prelude, loaded?.functions ?: emptyList())
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed && !disposed.get() && requestId == evaluationSeq.get()) {
                    applyResult(result)
                    if (loaded != null) updateFunctionsLabel(loaded) else functionsLabel.text = " "
                }
            }
        }
    }

    private fun updateFunctionsLabel(loaded: JsonataFunctionLoader.LoadResult) {
        val names = loaded.functions.map { "\$${it.name}" }
        functionsLabel.foreground = JBColor.GRAY
        when {
            loaded.errors.isNotEmpty() -> {
                functionsLabel.text = "⚠ Custom functions: ${loaded.errors.joinToString("; ")}"
                functionsLabel.foreground = JBColor.RED
            }
            names.isNotEmpty() -> functionsLabel.text = "Custom functions: ${names.joinToString(", ")}"
            else -> functionsLabel.text = " "
        }
        functionsLabel.toolTipText = functionsLabel.text.trim().ifEmpty { null }
    }

    private fun applyResult(result: JsonataEvaluator.Result) {
        when (result) {
            is JsonataEvaluator.Result.Success -> {
                setResultText(result.pretty)
                statusLabel.text = result.note ?: " "
                statusLabel.toolTipText = result.note
                statusLabel.foreground = JBColor.GRAY
            }
            is JsonataEvaluator.Result.Failure -> {
                val message = "${result.phase.label} error: ${result.message}"
                statusLabel.text = message
                statusLabel.toolTipText = message
                statusLabel.foreground = JBColor.RED
            }
        }
    }

    private fun setResultText(text: String) {
        ApplicationManager.getApplication().runWriteAction {
            resultDocument.setText(text)
        }
    }

    private fun configureExpressionField() {
        expressionField.setPlaceholder("e.g.  account.order.product.price   or   \$sum(items.price)")
        expressionField.setPreferredWidth(120)
        expressionField.preferredSize = Dimension(120, 80)
        expressionField.border = JBUI.Borders.empty(2)
        // Let the completion contributor reach the bound JSON via the expression editor's document.
        expressionField.document.putUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) {
            sourceDocument?.immutableCharSequence?.toString() ?: ""
        }
    }

    private fun configureResultEditor() {
        resultEditor.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = true
            isLineMarkerAreaShown = false
            isRightMarginShown = false
            isUseSoftWraps = false
        }
        val jsonType = FileTypeManager.getInstance().getFileTypeByExtension("json")
        resultEditor.highlighter =
            EditorHighlighterFactory.getInstance().createEditorHighlighter(project, jsonType)
        resultEditor.setBorder(JBUI.Borders.empty(2))
    }

    private fun section(title: String, body: JComponent, footer: JComponent? = null): JComponent =
        JPanel(BorderLayout()).apply {
            add(JBLabel(title).apply {
                border = JBUI.Borders.empty(3, 6)
                foreground = JBColor.GRAY
            }, BorderLayout.NORTH)
            add(body, BorderLayout.CENTER)
            if (footer != null) {
                footer.border = JBUI.Borders.empty(2, 6)
                add(footer, BorderLayout.SOUTH)
            }
        }

    /** The component focus should land on when the playground is shown. */
    fun preferredFocusComponent(): JComponent = expressionField

    override fun dispose() {
        disposed.set(true)
        evaluationSeq.incrementAndGet()
        alarm.cancelAllRequests()
        sourceListener?.let { old -> sourceDocument?.removeDocumentListener(old) }
        editorFactory.releaseEditor(resultEditor)
    }

    companion object {
        private const val DEBOUNCE_MS = 250
        private const val NO_SOURCE_TEXT =
            "No JSON bound. Run \"Open JSONata Playground\" from a JSON file."
    }
}

/**
 * A label that never forces its container wider: its minimum width is 0 and its preferred width is
 * clamped to the parent's current width, so a long single-line message (e.g. a JSONata error) is
 * clipped with an ellipsis instead of stretching the whole split editor. The full text stays
 * available via the tooltip.
 */
private class ElasticLabel(text: String) : JBLabel(text) {
    override fun getMinimumSize(): Dimension = Dimension(0, super.getMinimumSize().height)

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        val cap = parent?.width ?: 0
        return if (cap in 1 until size.width) Dimension(cap, size.height) else size
    }
}
