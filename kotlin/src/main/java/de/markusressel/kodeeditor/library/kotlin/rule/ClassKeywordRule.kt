package de.markusressel.kodeeditor.library.kotlin.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class ClassKeywordRule : SyntaxHighlighterRule {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "(companion\\s+)?object(?=\\s)|(data\\s+)?class(?=\\s)"
                .toRegex()
    }

}