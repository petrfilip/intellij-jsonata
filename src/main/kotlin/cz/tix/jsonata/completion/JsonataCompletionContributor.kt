package cz.tix.jsonata.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import cz.tix.jsonata.CUSTOM_FUNCTIONS_ENABLED
import cz.tix.jsonata.settings.JsonataFunctionLoader

/**
 * Completion for the JSONata expression, gated by the caret context (see [JsonataExpressionScanner]):
 *  - after `$`            -> built-in functions;
 *  - after a `.` step     -> field names from the bound JSON (evaluated container, see [JsonataFieldKeys]);
 *  - value position       -> root field names + functions + keywords.
 */
class JsonataCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), JsonataCompletionProvider())
    }
}

/** Shared singleton touched from completion threads — hence [JsonataFieldKeys.keys] is `@Synchronized`. */
private val FIELD_KEYS = JsonataFieldKeys()

private val IDENT = Regex("[A-Za-z_][A-Za-z0-9_]*")

private val RESERVED = JsonataBuiltins.KEYWORDS.toSet()

private class JsonataCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        ProgressManager.checkCanceled()
        val document = parameters.editor.document
        val text = document.immutableCharSequence.toString()
        val offset = parameters.offset.coerceIn(0, text.length)

        val rs = result.withPrefixMatcher(prefixAt(text, offset))
        val ctx = JsonataExpressionScanner.analyze(text, offset)

        when (ctx.mode) {
            JsonataExpressionScanner.Mode.NONE -> return
            JsonataExpressionScanner.Mode.FUNCTION -> addFunctions(rs, parameters.editor.project)
            JsonataExpressionScanner.Mode.FIELD -> addFields(rs, ctx.containerExpr, document)
            JsonataExpressionScanner.Mode.VALUE -> {
                // At a value position the implicit (block) context's fields are valid too.
                addFields(rs, ctx.containerExpr, document)
                addFunctions(rs, parameters.editor.project)
                addKeywords(rs)
            }
        }
    }

    private fun addFields(rs: CompletionResultSet, container: String?, document: Document) {
        val supplier = document.getUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) ?: return
        val jsonText = supplier()
        ProgressManager.checkCanceled()
        for (key in FIELD_KEYS.keys(container, jsonText)) {
            rs.addElement(fieldElement(key))
        }
    }

    private fun addFunctions(rs: CompletionResultSet, project: Project?) {
        for (fn in JsonataBuiltins.FUNCTIONS) {
            rs.addElement(
                LookupElementBuilder.create(fn.name)
                    .withIcon(AllIcons.Nodes.Function)
                    .withTypeText(fn.signature, true)
                    .withInsertHandler(FUNCTION_INSERT)
            )
        }
        if (CUSTOM_FUNCTIONS_ENABLED && project != null) {
            for (custom in JsonataFunctionLoader.getInstance(project).load().functions) {
                rs.addElement(
                    LookupElementBuilder.create("\$${custom.name}")
                        .withIcon(AllIcons.Nodes.Function)
                        .withTypeText("custom", true)
                        .withInsertHandler(FUNCTION_INSERT)
                )
            }
        }
    }

    private fun addKeywords(rs: CompletionResultSet) {
        for (kw in JsonataBuiltins.KEYWORDS) {
            rs.addElement(LookupElementBuilder.create(kw).bold())
        }
    }

    /**
     * Lookup element for a field [key]. A key that is not a plain identifier OR collides with a reserved
     * keyword is wrapped in backticks (``` `key` ```) via a custom insert handler so it stays valid JSONata.
     */
    private fun fieldElement(key: String): LookupElement {
        val needsQuote = !IDENT.matches(key) || key in RESERVED
        val element = LookupElementBuilder.create(key)
            .withIcon(AllIcons.Nodes.Field)
            .withTypeText("field", true)
        if (!needsQuote) return element
        val insertText = "`$key`"
        return element.withInsertHandler { ctx, _ ->
            ctx.document.replaceString(ctx.startOffset, ctx.tailOffset, insertText)
        }
    }

    private fun prefixAt(text: String, offset: Int): String {
        var start = offset
        while (start > 0 && isTokenChar(text[start - 1])) start--
        return text.substring(start, offset)
    }

    private fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

    companion object {
        /** Appends `()` after a function name and places the caret between the parentheses. */
        private val FUNCTION_INSERT = InsertHandler<LookupElement> { ctx, _ ->
            val document = ctx.document
            val tail = ctx.tailOffset
            val hasParen = tail < document.textLength && document.charsSequence[tail] == '('
            if (!hasParen) document.insertString(tail, "()")
            ctx.editor.caretModel.moveToOffset(tail + 1)
        }
    }
}
