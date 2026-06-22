package cz.tix.jsonata.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Hand-written lexer for JSONata. Produces a flat token stream used for syntax highlighting and as
 * the basis for the parser definition. Position/escape handling mirrors the JSONata tokenizer
 * closely enough for highlighting; the dashjoin engine remains the source of truth for semantics.
 *
 * Regex literals are recognized in value positions (`$match(x, /.../)`) and `/` remains an operator
 * after value tokens (`a / b`).
 */
class JsonataLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }
        tokenType = scan()
    }

    private fun scan(): IElementType {
        val c = buffer[tokenStart]
        return when {
            c.isWhitespace() -> consumeWhitespace()
            c == '/' && peek(1) == '*' -> consumeBlockComment()
            c == '/' && canStartRegex(tokenStart) -> consumeRegex()
            c == '"' || c == '\'' -> consumeString(c)
            c == '`' -> consumeBacktick()
            c == '$' -> consumeVariable()
            c.isDigit() -> consumeNumber()
            isIdentStart(c) -> consumeIdentifierOrKeyword()
            else -> consumePunctuation()
        }
    }

    private fun consumeWhitespace(): IElementType {
        var i = tokenStart
        while (i < endOffset && buffer[i].isWhitespace()) i++
        tokenEnd = i
        return TokenType.WHITE_SPACE
    }

    private fun consumeBlockComment(): IElementType {
        var i = tokenStart + 2
        while (i < endOffset) {
            if (buffer[i] == '*' && i + 1 < endOffset && buffer[i + 1] == '/') {
                i += 2
                break
            }
            i++
        }
        tokenEnd = i
        return JsonataTypes.COMMENT
    }

    private fun consumeString(quote: Char): IElementType {
        var i = tokenStart + 1
        while (i < endOffset) {
            val ch = buffer[i]
            if (ch == '\\' && i + 1 < endOffset) {
                i += 2
                continue
            }
            if (ch == quote) {
                i++
                break
            }
            i++
        }
        tokenEnd = i
        return JsonataTypes.STRING
    }

    private fun consumeRegex(): IElementType {
        var i = tokenStart + 1
        var inClass = false
        while (i < endOffset) {
            val ch = buffer[i]
            when {
                ch == '\\' && i + 1 < endOffset -> {
                    i += 2
                    continue
                }
                ch == '[' -> inClass = true
                ch == ']' -> inClass = false
                ch == '/' && !inClass -> {
                    i++
                    while (i < endOffset && buffer[i].isLetter()) i++
                    break
                }
            }
            i++
        }
        tokenEnd = i
        return JsonataTypes.REGEX
    }

    private fun consumeBacktick(): IElementType {
        var i = tokenStart + 1
        while (i < endOffset && buffer[i] != '`') i++
        if (i < endOffset) i++ // include closing backtick
        tokenEnd = i
        return JsonataTypes.BACKTICK_NAME
    }

    private fun consumeVariable(): IElementType {
        var i = tokenStart + 1
        if (i < endOffset && buffer[i] == '$') i++ // $$ root
        while (i < endOffset && isIdentPart(buffer[i])) i++
        tokenEnd = i
        return JsonataTypes.VARIABLE
    }

    private fun consumeNumber(): IElementType {
        var i = tokenStart
        while (i < endOffset && buffer[i].isDigit()) i++
        // fraction — requires a digit after the `.` so the `..` range operator (`1..5`) isn't eaten as a float
        if (i < endOffset && buffer[i] == '.' && i + 1 < endOffset && buffer[i + 1].isDigit()) {
            i++
            while (i < endOffset && buffer[i].isDigit()) i++
        }
        // exponent
        if (i < endOffset && (buffer[i] == 'e' || buffer[i] == 'E')) {
            var j = i + 1
            if (j < endOffset && (buffer[j] == '+' || buffer[j] == '-')) j++
            if (j < endOffset && buffer[j].isDigit()) {
                j++
                while (j < endOffset && buffer[j].isDigit()) j++
                i = j
            }
        }
        tokenEnd = i
        return JsonataTypes.NUMBER
    }

    private fun consumeIdentifierOrKeyword(): IElementType {
        var i = tokenStart
        while (i < endOffset && isIdentPart(buffer[i])) i++
        tokenEnd = i
        return when (buffer.subSequence(tokenStart, i).toString()) {
            "true", "false" -> JsonataTypes.BOOLEAN
            "null" -> JsonataTypes.NULL
            "and", "or", "in", "function" -> JsonataTypes.KEYWORD
            else -> JsonataTypes.IDENTIFIER
        }
    }

    private fun consumePunctuation(): IElementType {
        val c = buffer[tokenStart]
        val c1 = peek(1)
        // two-character tokens first
        when {
            c == ':' && c1 == '=' -> return punct(2, JsonataTypes.ASSIGN)
            c == '~' && c1 == '>' -> return punct(2, JsonataTypes.OPERATOR)
            c == '.' && c1 == '.' -> return punct(2, JsonataTypes.RANGE)
            c == '!' && c1 == '=' -> return punct(2, JsonataTypes.OPERATOR)
            c == '<' && c1 == '=' -> return punct(2, JsonataTypes.OPERATOR)
            c == '>' && c1 == '=' -> return punct(2, JsonataTypes.OPERATOR)
            c == '*' && c1 == '*' -> return punct(2, JsonataTypes.OPERATOR) // descendant
        }
        return when (c) {
            '.' -> punct(1, JsonataTypes.DOT)
            '(' -> punct(1, JsonataTypes.LPAREN)
            ')' -> punct(1, JsonataTypes.RPAREN)
            '[' -> punct(1, JsonataTypes.LBRACKET)
            ']' -> punct(1, JsonataTypes.RBRACKET)
            '{' -> punct(1, JsonataTypes.LBRACE)
            '}' -> punct(1, JsonataTypes.RBRACE)
            ',' -> punct(1, JsonataTypes.COMMA)
            ';' -> punct(1, JsonataTypes.SEMICOLON)
            ':' -> punct(1, JsonataTypes.COLON)
            '+', '-', '*', '/', '%', '=', '<', '>', '&', '^', '?', '@', '#', '|', '~' ->
                punct(1, JsonataTypes.OPERATOR)
            else -> punct(1, TokenType.BAD_CHARACTER)
        }
    }

    private fun punct(length: Int, type: IElementType): IElementType {
        tokenEnd = tokenStart + length
        return type
    }

    private fun peek(ahead: Int): Char {
        val i = tokenStart + ahead
        return if (i < endOffset) buffer[i] else '\u0000'
    }

    private fun canStartRegex(index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && buffer[i].isWhitespace()) i--
        if (i < 0) return true
        val previous = buffer[i]
        if (previous in "([{,:;?=<>!&|+-*%^~") return true
        if (previous.isLetter()) {
            var start = i
            while (start >= 0 && isIdentPart(buffer[start])) start--
            val word = buffer.subSequence(start + 1, i + 1).toString()
            return word == "and" || word == "or" || word == "in"
        }
        return false
    }

    private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'
    private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}
