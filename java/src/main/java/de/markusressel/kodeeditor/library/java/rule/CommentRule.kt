package de.markusressel.kodeeditor.library.java.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

class CommentRule : SyntaxHighlighterRule {

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "//.*|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/"
                .toRegex()
    }

}