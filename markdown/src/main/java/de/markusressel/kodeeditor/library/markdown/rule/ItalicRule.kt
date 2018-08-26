package de.markusressel.kodeeditor.library.markdown.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class ItalicRule : SyntaxHighlighterRule {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(?<!\\*)\\*(?!\\*).+?\\*(?!\\*)"
                .toRegex()
    }

}