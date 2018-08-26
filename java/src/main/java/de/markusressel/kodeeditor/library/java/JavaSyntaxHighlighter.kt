package de.markusressel.kodeeditor.library.java

import de.markusressel.kodeeditor.library.java.colorscheme.DarkBackgroundColorScheme
import de.markusressel.kodeeditor.library.java.rule.*
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxColorScheme
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterBase
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class JavaSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getRules(): Set<SyntaxHighlighterRule> {
        return setOf(PackageKeywordRule(), ImportKeywordRule(), ClassKeywordRule(), AnnotationRule(), TypeKeywordRule(), ReturnKeywordRule(), VisibilityKeywordRule(), CommentRule())
    }

    override fun getDefaultColorScheme(): SyntaxColorScheme {
        return DarkBackgroundColorScheme()
    }

}