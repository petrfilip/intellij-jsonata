package cz.tix.jsonata.completion

/**
 * Determines the completion context at the caret inside a JSONata expression, WITHOUT a full
 * grammar — but string- and bracket-depth aware (a plain regex would give wrong results inside
 * predicates `[...]` and string/backtick literals).
 *
 * It answers two things:
 *  - [Mode]: are we typing a function (`$...`), a field after a `.` step, or in a value position?
 *  - For [Mode.FIELD], the [Ctx.containerExpr]: the maximal balanced expression to the left of the
 *    `.` whose evaluated result's keys should be offered (e.g. `account.order[price>5]`).
 *
 * Pure logic, no IntelliJ dependencies — unit-tested in JsonataExpressionScannerTest.
 */
object JsonataExpressionScanner {

    /**
     * Completion context kind at the caret:
     *  - [NONE]: inside a string literal or comment ⇒ suppress completion;
     *  - [FUNCTION]: after a `$` ⇒ offer function names;
     *  - [FIELD]: after a `.` path step ⇒ offer the container's field names;
     *  - [VALUE]: general value position ⇒ offer fields of the implicit context plus functions/keywords.
     */
    enum class Mode { NONE, FUNCTION, FIELD, VALUE }

    /** @param containerExpr expression whose keys to offer (for [Mode.FIELD]/[Mode.VALUE]); null = JSON root. */
    data class Ctx(val mode: Mode, val containerExpr: String?)

    /** Determines the completion [Ctx] at [caret] (clamped into [text]). */
    fun analyze(text: String, caret: Int): Ctx {
        val offset = caret.coerceIn(0, text.length)
        val mask = opaqueMask(text, offset)
        if (mask.insideAtEnd) return Ctx(Mode.NONE, null)
        val inStr = mask.opaque

        // The partial token being typed (letters/digits/_/$).
        var tokenStart = offset
        while (tokenStart > 0 && !inStr[tokenStart - 1] && isTokenChar(text[tokenStart - 1])) tokenStart--
        val partial = text.substring(tokenStart, offset)

        if (partial.startsWith("$")) return Ctx(Mode.FUNCTION, null)

        // Significant char before the token (skip whitespace).
        var p = tokenStart
        while (p > 0 && !inStr[p - 1] && text[p - 1].isWhitespace()) p--

        // Context established by any enclosing `path.( … )` block or `path[ … ]` predicate.
        val blockContext = blockContextAt(text, inStr, tokenStart)

        return if (p > 0 && !inStr[p - 1] && text[p - 1] == '.') {
            // field after a dot: combine the block context with the local dotted chain
            Ctx(Mode.FIELD, combine(blockContext, extractContainer(text, p - 1, inStr)))
        } else {
            // value position: fields of the (block) context are valid, plus functions/keywords
            Ctx(Mode.VALUE, blockContext?.ifBlank { null })
        }
    }

    /**
     * The implicit context expression at [pos], following enclosing blocks/predicates:
     *  - `EXPR.( … )` -> context is EXPR (the block is evaluated against each EXPR item)
     *  - `EXPR[ … ]`  -> context is EXPR (the predicate is evaluated against each element)
     *  - `$f( … )`, `( … )`, `{ … }` -> the context surrounding the opener (root at top level)
     * Returns null/blank for the JSON root.
     */
    private fun blockContextAt(text: String, inStr: BooleanArray, pos: Int): String? {
        val (openerIndex, openerChar) = innermostOpener(text, inStr, pos) ?: return null
        var b = openerIndex
        while (b > 0 && !inStr[b - 1] && text[b - 1].isWhitespace()) b--
        val before = if (b > 0 && !inStr[b - 1]) text[b - 1] else ' '

        // `path.( … )`, `path.[ … ]`, `path.{ … }` — block / constructor mapped over each item of path.
        if (before == '.') {
            return combine(blockContextAt(text, inStr, b - 1), extractContainer(text, b - 1, inStr))
        }
        // `path[ … ]` — predicate evaluated against each element of path.
        if (openerChar == '[' && isPathEndChar(before)) {
            return combine(blockContextAt(text, inStr, openerIndex), extractContainer(text, openerIndex, inStr))
        }
        // function arg / plain grouping / object|array literal — the surrounding context.
        return blockContextAt(text, inStr, openerIndex)
    }

    /** The nearest unbalanced opening bracket to the left of [pos], or null at top level. */
    private fun innermostOpener(text: String, inStr: BooleanArray, pos: Int): Pair<Int, Char>? {
        var i = pos - 1
        var depth = 0
        while (i >= 0) {
            if (inStr[i]) { i--; continue }
            when (text[i]) {
                ')', ']', '}' -> depth++
                '(', '[', '{' -> {
                    if (depth == 0) return i to text[i]
                    depth--
                }
            }
            i--
        }
        return null
    }

    private fun combine(block: String?, local: String?): String? = when {
        local.isNullOrBlank() -> block?.ifBlank { null }
        block.isNullOrBlank() -> local
        else -> "$block.$local"
    }

    private fun isPathEndChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == ')' || c == ']' || c == '`'

    /**
     * A function call enclosing the caret, used by parameter info (read by `JsonataParameterInfoHandler`).
     *
     * @property functionName the `$`-prefixed built-in name (e.g. `$sum`), fed to [JsonataBuiltins.byName].
     * @property parameterIndex zero-based index of the argument the caret is in (count of top-level commas
     *   since the open paren); used to highlight the active parameter.
     * @property openParenOffset document offset of the `(`, used to anchor the parameter-info hint.
     */
    data class Call(val functionName: String, val parameterIndex: Int, val openParenOffset: Int)

    /**
     * Finds the `$func(` call that directly encloses [caret] and the index of the argument the
     * caret is in (top-level commas since the open paren). A `{ … }` (lambda body / object literal)
     * is scanned through so a call wrapping it is still found, but its brace depth is tracked
     * separately so commas inside the body are not mistaken for top-level argument separators; an
     * unbalanced `[ … ]` predicate stops the search and yields null (the caret is not in a call's
     * argument list).
     */
    fun findEnclosingCall(text: String, caret: Int): Call? {
        val offset = caret.coerceIn(0, text.length)
        val mask = opaqueMask(text, offset)
        if (mask.insideAtEnd) return null
        val inStr = mask.opaque
        var i = offset - 1
        var depth = 0
        var braceDepth = 0
        var commas = 0
        while (i >= 0) {
            if (inStr[i]) { i--; continue }
            when (text[i]) {
                ')', ']' -> depth++
                '}' -> braceDepth++
                '(' -> {
                    if (depth == 0) {
                        val name = readFunctionNameBefore(text, i, inStr) ?: return null
                        return Call(name, commas, i)
                    }
                    depth--
                }
                // An unbalanced '[' at depth 0 is a predicate — the caret is not in a call arg list.
                '[' -> { if (depth == 0) return null else depth-- }
                // A '{' closing a balanced "{ }" body just steps brace depth back out. An unbalanced
                // '{' (braceDepth 0) is a lambda body / object-literal argument the caret is inside:
                // the enclosing `$func(` is still meaningful, so keep scanning outward (don't bail),
                // but every comma counted so far was inside that body — drop them so only commas
                // outside any brace body count as top-level argument separators.
                '{' -> if (braceDepth > 0) braceDepth-- else commas = 0
                ',' -> if (depth == 0 && braceDepth == 0) commas++
            }
            i--
        }
        return null
    }

    private fun readFunctionNameBefore(text: String, parenIndex: Int, inStr: BooleanArray): String? {
        var i = parenIndex - 1
        while (i >= 0 && !inStr[i] && text[i].isWhitespace()) i--
        val end = i + 1
        while (i >= 0 && !inStr[i] && (text[i].isLetterOrDigit() || text[i] == '_')) i--
        // Must be a "$" followed by at least one identifier char (so "$(" is not a call), and the
        // "$" must not itself be preceded by another "$" (so "$$count(" — the root variable — is
        // not mis-read as a "$count" call).
        if (i < 0 || text[i] != '$') return null
        if (i + 1 >= end) return null // no identifier char after '$'
        if (i > 0 && !inStr[i - 1] && text[i - 1] == '$') return null
        return text.substring(i, end)
    }

    private data class OpaqueMask(val opaque: BooleanArray, val insideAtEnd: Boolean)

    /**
     * Marks characters of `text[0, length)` that completion/scanning should ignore:
     * string/backtick literals, regex literals and block comments. [insideAtEnd] is true only when
     * the caret is still inside an open literal/comment, not merely after its closing delimiter.
     */
    private fun opaqueMask(text: String, length: Int): OpaqueMask {
        val mask = BooleanArray(length)
        var i = 0
        var open = false
        var quote = ' '
        var blockComment = false
        var regex = false
        var regexClass = false
        while (i < length) {
            val c = text[i]
            if (blockComment) {
                mask[i] = true
                if (c == '*' && i + 1 < length && text[i + 1] == '/') {
                    mask[i + 1] = true
                    blockComment = false
                    i += 2
                    continue
                }
            } else if (regex) {
                mask[i] = true
                when {
                    c == '\\' && i + 1 < length -> {
                        mask[i + 1] = true
                        i += 2
                        continue
                    }
                    c == '[' -> regexClass = true
                    c == ']' -> regexClass = false
                    c == '/' && !regexClass -> {
                        regex = false
                        var j = i + 1
                        while (j < length && text[j].isLetter()) {
                            mask[j] = true
                            j++
                        }
                        i = j
                        continue
                    }
                }
            } else if (open) {
                mask[i] = true
                // Backtick (field-name) literals have NO `\` escaping, so only skip an escaped char
                // inside `"`/`'` strings — hence the `quote != '`'` guard.
                if (c == '\\' && quote != '`' && i + 1 < length) {
                    mask[i + 1] = true
                    i += 2
                    continue
                }
                if (c == quote) open = false
            } else if (c == '/' && i + 1 < length && text[i + 1] == '*') {
                blockComment = true
                mask[i] = true
                mask[i + 1] = true
                i += 2
                continue
            } else if (c == '/' && canStartRegex(text, mask, i)) {
                regex = true
                regexClass = false
                mask[i] = true
            } else if (c == '"' || c == '\'' || c == '`') {
                open = true
                quote = c
                mask[i] = true
            }
            i++
        }
        return OpaqueMask(mask, open || blockComment || regex)
    }

    /**
     * The balanced primary expression immediately to the left of [dotIndex]: walks backwards,
     * tracking bracket depth, and stops at the first top-level operator/separator (see
     * [isOperatorOrSeparator]) — but deliberately keeps path binders (`.` `*` `%` `@` `#`) so the
     * full step chain is captured.
     */
    private fun extractContainer(text: String, dotIndex: Int, inStr: BooleanArray): String? {
        var i = dotIndex - 1
        while (i >= 0 && !inStr[i] && text[i].isWhitespace()) i--
        if (i < 0) return null

        val end = i + 1
        var start = end
        var depth = 0
        while (i >= 0) {
            if (inStr[i]) { start = i; i--; continue }
            val c = text[i]
            when {
                c == ')' || c == ']' || c == '}' -> { depth++; start = i; i-- }
                c == '(' || c == '[' || c == '{' -> {
                    if (depth == 0) break
                    depth--; start = i; i--
                }
                depth == 0 && isOperatorOrSeparator(text, i, inStr) -> break
                else -> { start = i; i-- }
            }
        }
        return text.substring(start, end).trim().ifBlank { null }
    }

    private fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun canStartRegex(text: String, mask: BooleanArray, slashIndex: Int): Boolean {
        var i = slashIndex - 1
        while (i >= 0 && !mask[i] && text[i].isWhitespace()) i--
        if (i < 0) return true
        val previous = text[i]
        if (previous in "([{,:;?=<>!&|+-*%^~") return true
        if (previous.isLetter()) {
            var start = i
            while (start >= 0 && !mask[start] && isTokenChar(text[start])) start--
            val word = text.substring(start + 1, i + 1)
            return word == "and" || word == "or" || word == "in"
        }
        return false
    }

    // A char that ends the backward container walk. `*`/`%` are ambiguous (multiply/modulo vs.
    // wildcard/parent step), so they count as separators only when NOT a path step. `.` `@` `#`
    // are never separators — they bind the path/context and stay part of the container expression.
    private fun isOperatorOrSeparator(text: String, index: Int, inStr: BooleanArray): Boolean {
        val c = text[index]
        if (c == '*' || c == '%') return !isPathStepOperator(text, index, inStr)
        return c in "+-/=<>!&|?:,;"
    }

    private fun isPathStepOperator(text: String, index: Int, inStr: BooleanArray): Boolean {
        if (previousSignificantChar(text, index, inStr) == '.') return true
        if (text[index] != '*') return false

        val previousStar = previousSignificantIndex(text, index, inStr)
            ?.takeIf { text[it] == '*' }
            ?: return false
        return previousSignificantChar(text, previousStar, inStr) == '.'
    }

    private fun previousSignificantChar(text: String, before: Int, inStr: BooleanArray): Char? {
        return previousSignificantIndex(text, before, inStr)?.let { text[it] }
    }

    private fun previousSignificantIndex(text: String, before: Int, inStr: BooleanArray): Int? {
        var i = before - 1
        while (i >= 0 && !inStr[i] && text[i].isWhitespace()) i--
        return if (i >= 0 && !inStr[i]) i else null
    }
}
