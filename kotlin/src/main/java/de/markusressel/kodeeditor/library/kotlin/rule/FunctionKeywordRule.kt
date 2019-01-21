package de.markusressel.kodeeditor.library.kotlin.rule

import android.text.Spannable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class FunctionKeywordRule : SyntaxHighlighterRule {

    override fun findMatches(spannable: Spannable): Sequence<MatchResult> {
        return PATTERN.findAll(spannable)
    }

    companion object {
        val PATTERN = "fun(?=\\s)".toRegex()
    }

}