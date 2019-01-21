package de.markusressel.kodeeditor.library.markdown.rule

import android.text.Spannable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class CodeLineRule : SyntaxHighlighterRule {

    override fun findMatches(spannable: Spannable): Sequence<MatchResult> {
        return PATTERN.findAll(spannable)
    }

    companion object {
        val PATTERN = "(?m)^ {4}.+".toRegex()
    }

}