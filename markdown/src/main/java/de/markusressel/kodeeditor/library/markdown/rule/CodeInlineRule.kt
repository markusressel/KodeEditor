package de.markusressel.kodeeditor.library.markdown.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class CodeInlineRule : SyntaxHighlighterRule {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(`{1,3})([^`]+?)\\1"
                .toRegex()
    }

}