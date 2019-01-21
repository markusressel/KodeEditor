package de.markusressel.kodeeditor.library.java.rule

import android.text.Spannable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class ReturnKeywordRule : SyntaxHighlighterRule {

    override fun findMatches(spannable: Spannable): Sequence<MatchResult> {
        return PATTERN.findAll(spannable)
    }

    companion object {
        val PATTERN = "return(?=\\s)".toRegex()
    }

}