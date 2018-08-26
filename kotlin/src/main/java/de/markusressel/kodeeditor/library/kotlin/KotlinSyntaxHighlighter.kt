package de.markusressel.kodeeditor.library.kotlin

import de.markusressel.kodeeditor.library.kotlin.colorscheme.DarkBackgroundColorScheme
import de.markusressel.kodeeditor.library.kotlin.rule.*
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxColorScheme
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterBase
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class KotlinSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getRules(): Set<SyntaxHighlighterRule> {
        return setOf(AnnotationRule(), ClassKeywordRule(), CommentRule(), ImportKeywordRule(), PackageKeywordRule(), ReturnKeywordRule(), FunctionKeywordRule(), VarKeywordRule(), NumberRule())
    }

    override fun getDefaultColorScheme(): SyntaxColorScheme {
        return DarkBackgroundColorScheme()
    }

}