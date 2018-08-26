package de.markusressel.kodeeditor.library.markdown.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class CodeInlineRule : SyntaxHighlighterRule {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        // TODO: This seems to be very inefficient, maybe there is a better way to detect such strings
        val PATTERN = "(`{1,3})([^`]+?)\\1"
                .toRegex()
    }

}