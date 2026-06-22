package cz.tix.jsonata.lang

import com.dashjoin.jsonata.JException
import com.dashjoin.jsonata.Jsonata
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Surfaces JSONata syntax errors as editor annotations by compiling the expression with the real
 * dashjoin engine — the errors therefore match actual JSONata semantics rather than a re-implemented
 * grammar. Compile-only (no JSON needed); runtime/evaluation errors are shown in the result panel.
 */
class JsonataAnnotator : Annotator {

    /**
     * Acts only on the [JsonataFile] root: compiles the whole expression text with the real engine.
     * Rethrows [ProcessCanceledException]; any other failure emits a single ERROR annotation. This is
     * compile-only — runtime/evaluation errors surface in the result panel, not here.
     */
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is JsonataFile) return
        val text = element.text
        if (text.isBlank()) return

        val error: Throwable = try {
            Jsonata.jsonata(text)
            return
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            e
        }

        val message = error.message?.takeIf { it.isNotBlank() } ?: "JSONata syntax error"
        holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(errorRange(text, error))
            .create()
    }

    // Picks a sensible squiggle range: start at the engine-reported location (fallback: end of text),
    // expand to the surrounding word; if that's empty, fall back to a single char, and finally to the
    // whole expression — so the error always lands on a real token.
    private fun errorRange(text: String, error: Throwable): TextRange {
        val length = text.length
        val pos = ((error as? JException)?.location ?: length).coerceIn(0, length)

        var start = pos
        while (start > 0 && isWordChar(text[start - 1])) start--
        var end = pos
        while (end < length && isWordChar(text[end])) end++

        if (start >= end) {
            start = (pos - 1).coerceAtLeast(0)
            end = pos.coerceAtMost(length)
        }
        return if (start < end) TextRange(start, end) else TextRange(0, length)
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'
}
