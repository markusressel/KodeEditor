package de.markusressel.kodeeditor.library.kotlin.rule

import android.text.Spannable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class VarKeywordRule : SyntaxHighlighterRule {

    override fun findMatches(spannable: Spannable): Sequence<MatchResult> {
        return PATTERN.findAll(spannable)
    }

    companion object {
        val PATTERN = "(lateinit\\s+)?var(?=\\s)|(const\\s+)?val(?=\\s)".toRegex()
    }

}