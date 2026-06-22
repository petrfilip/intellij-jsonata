package cz.tix.jsonata.lang

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [JsonataSyntaxHighlighter.getTokenHighlights].
 *
 * Like [JsonataLexerTest], these instantiate the highlighter directly: [IElementType] can be
 * constructed standalone and [com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey]
 * only touches a static registry, so no IntelliJ application runtime is required. Each token type is
 * mapped to a single [com.intellij.openapi.editor.colors.TextAttributesKey] whose stable
 * [com.intellij.openapi.editor.colors.TextAttributesKey.getExternalName] is asserted; unknown types
 * yield an empty array.
 */
class JsonataSyntaxHighlighterTest {

    private val highlighter = JsonataSyntaxHighlighter()

    /** External name of the single highlight key produced for [type]. */
    private fun externalName(type: IElementType): String {
        val keys = highlighter.getTokenHighlights(type)
        assertEquals(1, keys.size, "expected exactly one highlight key for $type")
        return keys[0].externalName
    }

    @Test
    fun `string maps to the string color`() {
        assertEquals("JSONATA_STRING", externalName(JsonataTypes.STRING))
    }

    @Test
    fun `regex maps to the string color`() {
        assertEquals("JSONATA_STRING", externalName(JsonataTypes.REGEX))
    }

    @Test
    fun `backtick name maps to the field color`() {
        assertEquals("JSONATA_FIELD", externalName(JsonataTypes.BACKTICK_NAME))
    }

    @Test
    fun `identifier maps to the field color`() {
        assertEquals("JSONATA_FIELD", externalName(JsonataTypes.IDENTIFIER))
    }

    @Test
    fun `number maps to the number color`() {
        assertEquals("JSONATA_NUMBER", externalName(JsonataTypes.NUMBER))
    }

    @Test
    fun `boolean and null map to the keyword color`() {
        assertEquals("JSONATA_KEYWORD", externalName(JsonataTypes.BOOLEAN))
        assertEquals("JSONATA_KEYWORD", externalName(JsonataTypes.NULL))
    }

    @Test
    fun `keyword maps to the keyword color`() {
        assertEquals("JSONATA_KEYWORD", externalName(JsonataTypes.KEYWORD))
    }

    @Test
    fun `comment maps to the comment color`() {
        assertEquals("JSONATA_COMMENT", externalName(JsonataTypes.COMMENT))
    }

    @Test
    fun `variable maps to the variable color`() {
        assertEquals("JSONATA_VARIABLE", externalName(JsonataTypes.VARIABLE))
    }

    @Test
    fun `assign operator and range all map to the operator color`() {
        assertEquals("JSONATA_OPERATOR", externalName(JsonataTypes.ASSIGN))
        assertEquals("JSONATA_OPERATOR", externalName(JsonataTypes.OPERATOR))
        assertEquals("JSONATA_OPERATOR", externalName(JsonataTypes.RANGE))
    }

    @Test
    fun `dot and colon map to the dot color`() {
        assertEquals("JSONATA_DOT", externalName(JsonataTypes.DOT))
        assertEquals("JSONATA_DOT", externalName(JsonataTypes.COLON))
    }

    @Test
    fun `comma and semicolon map to the comma color`() {
        assertEquals("JSONATA_COMMA", externalName(JsonataTypes.COMMA))
        assertEquals("JSONATA_COMMA", externalName(JsonataTypes.SEMICOLON))
    }

    @Test
    fun `parentheses map to their own color`() {
        assertEquals("JSONATA_PARENTHESES", externalName(JsonataTypes.LPAREN))
        assertEquals("JSONATA_PARENTHESES", externalName(JsonataTypes.RPAREN))
    }

    @Test
    fun `brackets map to their own color`() {
        assertEquals("JSONATA_BRACKETS", externalName(JsonataTypes.LBRACKET))
        assertEquals("JSONATA_BRACKETS", externalName(JsonataTypes.RBRACKET))
    }

    @Test
    fun `braces map to their own color`() {
        assertEquals("JSONATA_BRACES", externalName(JsonataTypes.LBRACE))
        assertEquals("JSONATA_BRACES", externalName(JsonataTypes.RBRACE))
    }

    @Test
    fun `bad character maps to the bad character color`() {
        assertEquals("JSONATA_BAD_CHARACTER", externalName(TokenType.BAD_CHARACTER))
    }

    @Test
    fun `unknown token type yields no highlight keys`() {
        val foreign = IElementType("FOREIGN_TOKEN", null)
        assertEquals(0, highlighter.getTokenHighlights(foreign).size)
    }

    @Test
    fun `whitespace token yields no highlight keys`() {
        assertEquals(0, highlighter.getTokenHighlights(TokenType.WHITE_SPACE).size)
    }
}
