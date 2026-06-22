package cz.tix.jsonata.lang

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [JsonataLexer].
 *
 * These tests instantiate the lexer directly and drive it via [JsonataLexer.start] / [advance].
 * They do not require an IntelliJ application runtime: [IElementType] and [com.intellij.lang.Language]
 * can be constructed standalone, and [com.intellij.psi.TokenType] exposes the shared
 * WHITE_SPACE / BAD_CHARACTER instances as plain constants. If a future platform change makes
 * IElementType registration require the application runtime, the maintainer can convert these to
 * BasePlatformTestCase / LexerTestCase; the assertions below are written as plain JUnit5.
 */
class JsonataLexerTest {

    /** Drives the lexer over [text], returning (tokenType, tokenText) pairs in order. */
    private fun tokens(text: String): List<Pair<IElementType, String>> {
        val lexer = JsonataLexer()
        lexer.start(text, 0, text.length, 0)
        val result = mutableListOf<Pair<IElementType, String>>()
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            val tokenText = text.substring(lexer.tokenStart, lexer.tokenEnd)
            result.add(type to tokenText)
            lexer.advance()
        }
        return result
    }

    /** Token types only, skipping whitespace. */
    private fun typesNoWs(text: String): List<IElementType> =
        tokens(text).filter { it.first != TokenType.WHITE_SPACE }.map { it.first }

    /** All token types, including whitespace. */
    private fun types(text: String): List<IElementType> = tokens(text).map { it.first }

    /** Asserts the lexer covered the whole input with no gaps or overlaps. */
    private fun assertFullCoverage(text: String) {
        val joined = tokens(text).joinToString("") { it.second }
        assertEquals(text, joined, "token texts must concatenate back to the input for: \"$text\"")
    }

    @Test
    fun `function call path has no whitespace tokens`() {
        assertEquals(
            listOf(
                JsonataTypes.VARIABLE,
                JsonataTypes.LPAREN,
                JsonataTypes.IDENTIFIER,
                JsonataTypes.DOT,
                JsonataTypes.IDENTIFIER,
                JsonataTypes.RPAREN,
            ),
            types("\$sum(a.b)"),
        )
        assertFullCoverage("\$sum(a.b)")
    }

    @Test
    fun `range operator is not consumed into the number`() {
        assertEquals(
            listOf(JsonataTypes.NUMBER, JsonataTypes.RANGE, JsonataTypes.NUMBER),
            types("1..5"),
        )
        assertEquals(
            listOf("1", "..", "5"),
            tokens("1..5").map { it.second },
        )
        assertFullCoverage("1..5")
    }

    @Test
    fun `numbers with fractions and exponents are single tokens`() {
        assertEquals(listOf(JsonataTypes.NUMBER), types("3.14"))
        assertEquals(listOf("3.14"), tokens("3.14").map { it.second })

        assertEquals(listOf(JsonataTypes.NUMBER), types("1e5"))
        assertEquals(listOf("1e5"), tokens("1e5").map { it.second })

        assertEquals(listOf(JsonataTypes.NUMBER), types("1.5e-3"))
        assertEquals(listOf("1.5e-3"), tokens("1.5e-3").map { it.second })

        assertFullCoverage("3.14")
        assertFullCoverage("1e5")
        assertFullCoverage("1.5e-3")
    }

    @Test
    fun `strings are single tokens`() {
        assertEquals(listOf(JsonataTypes.STRING), types("\"hello\""))
        assertEquals(listOf("\"hello\""), tokens("\"hello\"").map { it.second })

        assertEquals(listOf(JsonataTypes.STRING), types("'hi'"))
        assertEquals(listOf("'hi'"), tokens("'hi'").map { it.second })

        assertFullCoverage("\"hello\"")
        assertFullCoverage("'hi'")
    }

    @Test
    fun `escaped quote stays inside one string token`() {
        // Actual chars: " a \ " b "  ->  "a\"b"
        val src = "\"a\\\"b\""
        assertEquals(listOf(JsonataTypes.STRING), types(src))
        assertEquals(listOf(src), tokens(src).map { it.second })
        assertFullCoverage(src)
    }

    @Test
    fun `regex literal is a single token`() {
        val src = "/a\\d+/i"
        assertEquals(listOf(JsonataTypes.REGEX), types(src))
        assertEquals(listOf(src), tokens(src).map { it.second })
        assertFullCoverage(src)
    }

    @Test
    fun `regex character class can contain slash`() {
        val src = "\$match(name, /[/a-z]+/)"
        assertTrue(types(src).none { it == TokenType.BAD_CHARACTER }, "unexpected BAD_CHARACTER in: $src")
        assertTrue(tokens(src).any { it.first == JsonataTypes.REGEX && it.second == "/[/a-z]+/" })
        assertFullCoverage(src)
    }

    @Test
    fun `slash after a value remains an operator`() {
        assertEquals(
            listOf(JsonataTypes.IDENTIFIER, JsonataTypes.OPERATOR, JsonataTypes.IDENTIFIER),
            typesNoWs("a / b"),
        )
        assertFullCoverage("a / b")
    }

    @Test
    fun `backtick name is a single token`() {
        assertEquals(listOf(JsonataTypes.BACKTICK_NAME), types("`first name`"))
        assertEquals(listOf("`first name`"), tokens("`first name`").map { it.second })
        assertFullCoverage("`first name`")
    }

    @Test
    fun `boolean and null literals`() {
        assertEquals(listOf(JsonataTypes.BOOLEAN), types("true"))
        assertEquals(listOf(JsonataTypes.BOOLEAN), types("false"))
        assertEquals(listOf(JsonataTypes.NULL), types("null"))
    }

    @Test
    fun `keywords`() {
        assertEquals(listOf(JsonataTypes.KEYWORD), types("and"))
        assertEquals(listOf(JsonataTypes.KEYWORD), types("or"))
        assertEquals(listOf(JsonataTypes.KEYWORD), types("in"))
        assertEquals(listOf(JsonataTypes.KEYWORD), types("function"))
    }

    @Test
    fun `block comment is a single token`() {
        assertEquals(listOf(JsonataTypes.COMMENT), types("/* c */"))
        assertEquals(listOf("/* c */"), tokens("/* c */").map { it.second })
        assertFullCoverage("/* c */")
    }

    @Test
    fun `assignment skipping whitespace`() {
        assertEquals(
            listOf(JsonataTypes.IDENTIFIER, JsonataTypes.ASSIGN, JsonataTypes.NUMBER),
            typesNoWs("a := 1"),
        )
        assertFullCoverage("a := 1")
    }

    @Test
    fun `chain operator is an OPERATOR`() {
        assertEquals(
            listOf(JsonataTypes.IDENTIFIER, JsonataTypes.OPERATOR, JsonataTypes.IDENTIFIER),
            typesNoWs("a ~> b"),
        )
        assertEquals("~>", tokens("a ~> b").first { it.second == "~>" }.second)
    }

    @Test
    fun `comparison operators`() {
        assertEquals(
            listOf(JsonataTypes.IDENTIFIER, JsonataTypes.OPERATOR, JsonataTypes.NUMBER),
            typesNoWs("x >= 1"),
        )
        assertEquals(
            listOf(JsonataTypes.IDENTIFIER, JsonataTypes.OPERATOR, JsonataTypes.IDENTIFIER),
            typesNoWs("x != y"),
        )
        assertEquals("x", tokens("x >= 1").first().second)
        assertFullCoverage("x >= 1")
        assertFullCoverage("x != y")
    }

    @Test
    fun `descendant double-star is one OPERATOR`() {
        assertEquals(listOf(JsonataTypes.OPERATOR), types("**"))
        assertEquals(listOf("**"), tokens("**").map { it.second })
        assertFullCoverage("**")
    }

    @Test
    fun `dollar variants are VARIABLE`() {
        assertEquals(listOf(JsonataTypes.VARIABLE), types("\$\$"))
        assertEquals(listOf("\$\$"), tokens("\$\$").map { it.second })

        assertEquals(listOf(JsonataTypes.VARIABLE), types("\$"))
        assertEquals(listOf("\$"), tokens("\$").map { it.second })

        assertFullCoverage("\$\$")
        assertFullCoverage("\$")
    }

    @Test
    fun `brackets braces and parens`() {
        assertEquals(
            listOf(
                JsonataTypes.LBRACKET,
                JsonataTypes.RBRACKET,
                JsonataTypes.LBRACE,
                JsonataTypes.RBRACE,
                JsonataTypes.LPAREN,
                JsonataTypes.RPAREN,
            ),
            types("[]{}()"),
        )
        assertFullCoverage("[]{}()")
    }

    @Test
    fun `stray backslash is a bad character`() {
        assertEquals(listOf(TokenType.BAD_CHARACTER), types("\\"))
        assertEquals(listOf("\\"), tokens("\\").map { it.second })
        assertFullCoverage("\\")
    }

    @Test
    fun `unterminated string runs to end of input as a single token`() {
        val src = "\"abc"
        assertEquals(listOf(JsonataTypes.STRING), types(src))
        assertEquals(listOf(src), tokens(src).map { it.second })
        assertFullCoverage(src)
    }

    @Test
    fun `unterminated backtick name runs to end of input as a single token`() {
        val src = "`abc"
        assertEquals(listOf(JsonataTypes.BACKTICK_NAME), types(src))
        assertEquals(listOf(src), tokens(src).map { it.second })
        assertFullCoverage(src)
    }

    @Test
    fun `unterminated block comment runs to end of input as a single token`() {
        val src = "/* abc"
        assertEquals(listOf(JsonataTypes.COMMENT), types(src))
        assertEquals(listOf(src), tokens(src).map { it.second })
        assertFullCoverage(src)
    }

    @Test
    fun `trailing backslash at end of string stays inside the string token`() {
        // " a b \   -> the backslash has no following char, so the string just ends at EOF.
        val src = "\"ab\\"
        assertEquals(listOf(JsonataTypes.STRING), types(src))
        assertEquals(listOf(src), tokens(src).map { it.second })
        assertFullCoverage(src)
    }

    @Test
    fun `dollar name is a single VARIABLE token`() {
        assertEquals(listOf(JsonataTypes.VARIABLE), types("\$foo"))
        assertEquals(listOf("\$foo"), tokens("\$foo").map { it.second })
        assertFullCoverage("\$foo")
    }

    @Test
    fun `identifier may contain digits`() {
        assertEquals(listOf(JsonataTypes.IDENTIFIER), types("a1b2"))
        assertEquals(listOf("a1b2"), tokens("a1b2").map { it.second })
        assertFullCoverage("a1b2")
    }

    @Test
    fun `number without exponent digit splits into NUMBER and IDENTIFIER`() {
        // "1e" -> the 'e' has no following digit, so it is not consumed into the number.
        assertEquals(listOf(JsonataTypes.NUMBER, JsonataTypes.IDENTIFIER), types("1e"))
        assertEquals(listOf("1", "e"), tokens("1e").map { it.second })
        assertFullCoverage("1e")
    }

    @Test
    fun `leading dot before digits is a DOT then a NUMBER`() {
        // ".5" -> the number scanner only starts on a digit, so '.' is a standalone DOT.
        assertEquals(listOf(JsonataTypes.DOT, JsonataTypes.NUMBER), types(".5"))
        assertEquals(listOf(".", "5"), tokens(".5").map { it.second })
        assertFullCoverage(".5")
    }

    @Test
    fun `colon and walrus assignment are distinct tokens`() {
        assertEquals(listOf(JsonataTypes.COLON), types(":"))
        assertEquals(listOf(":"), tokens(":").map { it.second })

        assertEquals(listOf(JsonataTypes.ASSIGN), types(":="))
        assertEquals(listOf(":="), tokens(":=").map { it.second })

        assertFullCoverage(":")
        assertFullCoverage(":=")
    }

    @Test
    fun `empty input produces no tokens`() {
        assertEquals(emptyList<IElementType>(), types(""))
        assertEquals(emptyList<Pair<IElementType, String>>(), tokens(""))
    }

    @Test
    fun `underscore-prefixed identifier is an IDENTIFIER`() {
        assertEquals(listOf(JsonataTypes.IDENTIFIER), types("_x"))
        assertEquals(listOf("_x"), tokens("_x").map { it.second })
        assertFullCoverage("_x")
    }

    @Test
    fun `mixed expression covers whole input`() {
        val src = "\$sum(account.order.price) >= 1.5e-3 and `n`"
        assertFullCoverage(src)
        // sanity: no BAD_CHARACTER produced for a well-formed expression
        assertTrue(types(src).none { it == TokenType.BAD_CHARACTER }, "unexpected BAD_CHARACTER in: $src")
    }
}
