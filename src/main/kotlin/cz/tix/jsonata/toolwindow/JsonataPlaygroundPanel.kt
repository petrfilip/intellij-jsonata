package cz.tix.jsonata.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.LanguageTextField
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
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
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * JSONata expression editor and read-only result viewer bound to one source JSON document.
 * Evaluation is debounced, runs off the EDT and is actively cancelled when superseded.
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

    private val sourceLabel = ElasticLabel(NO_SOURCE_TEXT)
    private val functionsLabel = ElasticLabel(" ")
    private val statusLabel = ElasticLabel(" ")
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)

    private var sourceDocument: Document? = null
    private var sourceListener: DocumentListener? = null
    private var sourceFileUrl: String? = null
    private val disposed = AtomicBoolean()
    private val evaluationSeq = AtomicLong()
    private val evaluationTask = AtomicReference<Future<*>?>()

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

        project.messageBus.connect(this).subscribe(JSONATA_SETTINGS_TOPIC, JsonataSettingsListener {
            JsonataFunctionLoader.getInstance(project).invalidate()
            scheduleEvaluation()
        })
    }

    fun bindSource(document: Document, file: VirtualFile?) {
        sourceListener?.let { old -> sourceDocument?.removeDocumentListener(old) }
        sourceDocument = document
        val listener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleEvaluation()
        }
        document.addDocumentListener(listener)
        sourceListener = listener
        sourceFileUrl = file?.url
        sourceLabel.text = "Source JSON: ${file?.name ?: "(in-memory document)"}"
        restoreExpression()
        scheduleEvaluation()
    }

    private fun restoreExpression() {
        val saved = sourceFileUrl?.let { JsonataExpressionStore.getInstance(project).get(it) } ?: return
        if (saved != expressionField.text) expressionField.text = saved
    }

    private fun persistExpression() {
        val url = sourceFileUrl ?: return
        JsonataExpressionStore.getInstance(project).put(url, expressionField.text)
    }

    private fun scheduleEvaluation() {
        if (disposed.get()) return
        val requestId = evaluationSeq.incrementAndGet()
        alarm.cancelAllRequests()
        evaluationTask.getAndSet(null)?.cancel(true)
        alarm.addRequest({ evaluate(requestId) }, DEBOUNCE_MS)
    }

    private fun evaluate(requestId: Long) {
        if (project.isDisposed || disposed.get() || requestId != evaluationSeq.get()) return
        val jsonText = sourceDocument?.immutableCharSequence?.toString() ?: ""
        val expression = expressionField.text
        val prelude = if (CUSTOM_FUNCTIONS_ENABLED) {
            JsonataProjectSettings.getInstance(project).state.prelude ?: ""
        } else {
            ""
        }

        val task = ApplicationManager.getApplication().executeOnPooledThread {
            if (disposed.get() || project.isDisposed || requestId != evaluationSeq.get()) {
                return@executeOnPooledThread
            }
            val loaded = if (CUSTOM_FUNCTIONS_ENABLED) JsonataFunctionLoader.getInstance(project).load() else null
            val result = evaluator.evaluate(jsonText, expression, prelude, loaded?.functions ?: emptyList())
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed && !disposed.get() && requestId == evaluationSeq.get()) {
                    applyResult(result)
                    if (loaded != null) updateFunctionsLabel(loaded) else functionsLabel.text = " "
                }
            }
        }
        evaluationTask.getAndSet(task)?.cancel(true)
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
                setResultText("")
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
        resultEditor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, jsonType)
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

    fun preferredFocusComponent(): JComponent = expressionField

    override fun dispose() {
        disposed.set(true)
        evaluationSeq.incrementAndGet()
        alarm.cancelAllRequests()
        evaluationTask.getAndSet(null)?.cancel(true)
        sourceListener?.let { old -> sourceDocument?.removeDocumentListener(old) }
        editorFactory.releaseEditor(resultEditor)
    }

    companion object {
        private const val DEBOUNCE_MS = 250
        private const val NO_SOURCE_TEXT =
            "No JSON bound. Run \"Open JSONata Playground\" from a JSON file."
    }
}

private class ElasticLabel(text: String) : JBLabel(text) {
    override fun getMinimumSize(): Dimension = Dimension(0, super.getMinimumSize().height)

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        val cap = parent?.width ?: 0
        return if (cap in 1 until size.width) Dimension(cap, size.height) else size
    }
}
