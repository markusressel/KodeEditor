package de.markusressel.kodeeditor.library.kotlin.rule

import android.text.Spannable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class ClassKeywordRule : SyntaxHighlighterRule {

    override fun findMatches(spannable: Spannable): Sequence<MatchResult> {
        return PATTERN.findAll(spannable)
    }

    companion object {
        val PATTERN = "(companion\\s+)?object(?=\\s)|(data\\s+)?class(?=\\s)".toRegex()
    }

}