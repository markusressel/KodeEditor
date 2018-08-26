package de.markusressel.kodeeditor.library.markdown.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class CodeLineRule : SyntaxHighlighterRule {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(?m)^ {4}.+"
                .toRegex()
    }

}