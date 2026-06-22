package cz.tix.jsonata.lang

import com.intellij.psi.tree.IElementType

/** An [IElementType] belonging to the JSONata language. */
class JsonataTokenType(debugName: String) : IElementType(debugName, JsonataLanguage) {
    override fun toString(): String = "JsonataTokenType.${super.toString()}"
}

/** All JSONata lexer token types. */
object JsonataTypes {
    @JvmField val IDENTIFIER = JsonataTokenType("IDENTIFIER")     // field name
    @JvmField val VARIABLE = JsonataTokenType("VARIABLE")         // $, $$, $name
    @JvmField val STRING = JsonataTokenType("STRING")             // "..." / '...'
    @JvmField val BACKTICK_NAME = JsonataTokenType("BACKTICK_NAME") // `...`
    @JvmField val REGEX = JsonataTokenType("REGEX")               // /.../i
    @JvmField val NUMBER = JsonataTokenType("NUMBER")
    @JvmField val BOOLEAN = JsonataTokenType("BOOLEAN")           // true / false
    @JvmField val NULL = JsonataTokenType("NULL")                 // null
    @JvmField val KEYWORD = JsonataTokenType("KEYWORD")           // and / or / in / function
    @JvmField val COMMENT = JsonataTokenType("COMMENT")           // /* ... */

    @JvmField val DOT = JsonataTokenType(".")
    @JvmField val RANGE = JsonataTokenType("..")
    @JvmField val LPAREN = JsonataTokenType("(")
    @JvmField val RPAREN = JsonataTokenType(")")
    @JvmField val LBRACKET = JsonataTokenType("[")
    @JvmField val RBRACKET = JsonataTokenType("]")
    @JvmField val LBRACE = JsonataTokenType("{")
    @JvmField val RBRACE = JsonataTokenType("}")
    @JvmField val COMMA = JsonataTokenType(",")
    @JvmField val SEMICOLON = JsonataTokenType(";")
    @JvmField val COLON = JsonataTokenType(":")
    @JvmField val ASSIGN = JsonataTokenType(":=")
    @JvmField val OPERATOR = JsonataTokenType("OPERATOR") // + - * / % = != < <= > >= & ^ ? @ # | ~> **
}
