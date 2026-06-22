package cz.tix.jsonata.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/** Maps JSONata token types to [TextAttributesKey]s (lexer-based highlighting). */
class JsonataSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = JsonataLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        JsonataTypes.STRING -> STRING
        JsonataTypes.REGEX -> STRING
        JsonataTypes.BACKTICK_NAME -> FIELD
        JsonataTypes.NUMBER -> NUMBER
        JsonataTypes.BOOLEAN, JsonataTypes.NULL -> KEYWORD
        JsonataTypes.KEYWORD -> KEYWORD
        JsonataTypes.COMMENT -> COMMENT
        JsonataTypes.VARIABLE -> VARIABLE
        JsonataTypes.IDENTIFIER -> FIELD
        JsonataTypes.OPERATOR, JsonataTypes.ASSIGN, JsonataTypes.RANGE -> OPERATOR
        JsonataTypes.DOT, JsonataTypes.COLON -> DOT
        JsonataTypes.COMMA, JsonataTypes.SEMICOLON -> COMMA
        JsonataTypes.LPAREN, JsonataTypes.RPAREN -> PARENTHESES
        JsonataTypes.LBRACKET, JsonataTypes.RBRACKET -> BRACKETS
        JsonataTypes.LBRACE, JsonataTypes.RBRACE -> BRACES
        TokenType.BAD_CHARACTER -> BAD_CHARACTER
        else -> EMPTY
    }

    companion object {
        val KEYWORD = key("JSONATA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val STRING = key("JSONATA_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER = key("JSONATA_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val COMMENT = key("JSONATA_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
        val VARIABLE = key("JSONATA_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val FIELD = key("JSONATA_FIELD", DefaultLanguageHighlighterColors.IDENTIFIER)
        val OPERATOR = key("JSONATA_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val DOT = key("JSONATA_DOT", DefaultLanguageHighlighterColors.DOT)
        val COMMA = key("JSONATA_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val PARENTHESES = key("JSONATA_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS = key("JSONATA_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val BRACES = key("JSONATA_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val BAD_CHARACTER = key("JSONATA_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private fun key(name: String, fallback: TextAttributesKey): Array<TextAttributesKey> =
            arrayOf(createTextAttributesKey(name, fallback))

        private val EMPTY = emptyArray<TextAttributesKey>()
    }
}
