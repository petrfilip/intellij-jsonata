package cz.tix.jsonata.lang

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiElement
import cz.tix.jsonata.completion.JsonataBuiltins
import cz.tix.jsonata.completion.JsonataExpressionScanner

/**
 * Shows the signature of the built-in JSONata function whose argument list the caret is in, with the
 * current argument highlighted. The enclosing call and argument index are derived from the text via
 * [JsonataExpressionScanner.findEnclosingCall] (no full grammar needed).
 */
class JsonataParameterInfoHandler : ParameterInfoHandler<PsiElement, JsonataBuiltins.Fn> {

    /** Locates the enclosing built-in call ([JsonataExpressionScanner.findEnclosingCall] + [JsonataBuiltins.byName]) to show its signature. */
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val text = context.editor.document.immutableCharSequence.toString()
        val call = JsonataExpressionScanner.findEnclosingCall(text, context.offset) ?: return null
        val fn = JsonataBuiltins.byName(call.functionName) ?: return null
        context.itemsToShow = arrayOf<Any>(fn)
        val file = context.file ?: return null
        return file.findElementAt(call.openParenOffset.coerceIn(0, (text.length - 1).coerceAtLeast(0))) ?: file
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, context.offset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        val file = context.file ?: return null
        return file.findElementAt(context.offset) ?: file
    }

    /** Re-resolves the enclosing call and updates the highlighted argument, or hides the hint once the caret leaves a known call. */
    override fun updateParameterInfo(element: PsiElement, context: UpdateParameterInfoContext) {
        val text = context.editor.document.immutableCharSequence.toString()
        val call = JsonataExpressionScanner.findEnclosingCall(text, context.offset)
        if (call == null || JsonataBuiltins.byName(call.functionName) == null) {
            context.removeHint()
            return
        }
        context.setCurrentParameter(call.parameterIndex)
    }

    /** Renders the parameter list with the current argument highlighted; shows `<no parameters>` for zero-arg functions. */
    override fun updateUI(p: JsonataBuiltins.Fn, context: ParameterInfoUIContext) {
        fun present(text: String, hlStart: Int, hlEnd: Int) =
            context.setupUIComponentPresentation(text, hlStart, hlEnd, false, false, false, context.defaultParameterColor)

        if (p.params.isEmpty()) {
            present("<no parameters>", -1, -1)
            return
        }
        val sb = StringBuilder()
        var highlightStart = -1
        var highlightEnd = -1
        val current = context.currentParameterIndex
        p.params.forEachIndexed { i, param ->
            if (i > 0) sb.append(", ")
            val start = sb.length
            sb.append(param.display())
            if (i == current) {
                highlightStart = start
                highlightEnd = sb.length
            }
        }
        present(sb.toString(), highlightStart, highlightEnd)
    }
}
