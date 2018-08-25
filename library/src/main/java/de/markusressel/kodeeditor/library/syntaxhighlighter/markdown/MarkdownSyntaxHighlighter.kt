package de.markusressel.kodeeditor.library.syntaxhighlighter.markdown

import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterBase
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SyntaxColorScheme
import de.markusressel.kodeeditor.library.syntaxhighlighter.markdown.rule.*
import de.markusressel.kodeeditor.library.syntaxhighlighter.markdown.colorscheme.DarkBackgroundColorScheme
import de.markusressel.mkdocseditor.syntaxhighlighter.markdown.rule.*

class MarkdownSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getRules(): Set<SyntaxHighlighterRule> {
        return setOf(HeadingRule(), ItalicRule(), BoldRule(), CodeInlineRule(), CodeLineRule(), TextLinkRule(), ImageLinkRule(), StrikeRule())
    }

    override fun getDefaultColorScheme(): SyntaxColorScheme {
        return DarkBackgroundColorScheme()
    }

}