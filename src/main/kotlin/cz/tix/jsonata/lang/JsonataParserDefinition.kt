package cz.tix.jsonata.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Minimal ("flat") parser definition: the lexer drives highlighting and a single root node holds
 * all tokens. This is enough for a PsiFile to exist so that highlighting, the annotator, parameter
 * info and completion can attach to the JSONata language. A full structural grammar (Grammar-Kit
 * BNF → PSI) is a possible future milestone; correctness of expressions is enforced by the engine.
 */
class JsonataParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = JsonataLexer()
    override fun createParser(project: Project?): PsiParser = JsonataParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getStringLiteralElements(): TokenSet = STRINGS
    override fun getWhitespaceTokens(): TokenSet = WHITESPACE
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = JsonataFile(viewProvider)

    companion object {
        val FILE = IFileElementType(JsonataLanguage)
        val COMMENTS: TokenSet = TokenSet.create(JsonataTypes.COMMENT)
        // BACKTICK_NAME (and REGEX) are grouped with STRINGS so the platform treats them as literals.
        val STRINGS: TokenSet = TokenSet.create(JsonataTypes.STRING, JsonataTypes.BACKTICK_NAME, JsonataTypes.REGEX)
        val WHITESPACE: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
    }
}

/** Trivial parser: consumes the whole token stream under one root node so a PsiFile exists. */
class JsonataParser : PsiParser {
    override fun parse(root: IElementType, builder: com.intellij.lang.PsiBuilder): ASTNode {
        val marker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        return builder.treeBuilt
    }
}
