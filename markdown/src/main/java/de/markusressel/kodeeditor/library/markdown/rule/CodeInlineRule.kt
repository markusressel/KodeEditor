package de.markusressel.kodeeditor.library.markdown.rule

import android.text.Spannable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class CodeInlineRule : SyntaxHighlighterRule {

    override fun findMatches(spannable: Spannable): Sequence<MatchResult> {
        return PATTERN.findAll(spannable)
    }

    companion object {
        // TODO: This seems to be very inefficient, maybe there is a better way to detect such strings
        val PATTERN = "(`{1,3})([^`]+?)\\1".toRegex()
    }

}