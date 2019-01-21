package de.markusressel.kodeeditor.library.markdown.rule

import android.text.Spannable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class StrikeRule : SyntaxHighlighterRule {

    override fun findMatches(spannable: Spannable): Sequence<MatchResult> {
        return PATTERN.findAll(spannable)
    }

    companion object {
        val PATTERN = "(~{2})([^~]+?)\\1".toRegex()
    }

}