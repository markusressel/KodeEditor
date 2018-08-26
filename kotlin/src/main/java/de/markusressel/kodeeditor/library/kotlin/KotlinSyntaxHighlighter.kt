package de.markusressel.kodeeditor.library.kotlin

import de.markusressel.kodeeditor.library.java.colorscheme.DarkBackgroundColorScheme
import de.markusressel.kodeeditor.library.java.rule.*
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxColorScheme
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterBase
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class KotlinSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getRules(): Set<SyntaxHighlighterRule> {
        return setOf(PackageKeywordRule(), ImportKeywordRule(), AnnotationRule(), TypeKeywordRule(), VisibilityKeywordRule())
    }

    override fun getDefaultColorScheme(): SyntaxColorScheme {
        return DarkBackgroundColorScheme()
    }

}