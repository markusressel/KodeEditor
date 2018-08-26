package de.markusressel.kodeeditor.library.syntaxhighlighter

import android.text.Editable

interface SyntaxHighlighterRule {

    /**
     * Find segments in the editable that are affected by this rule
     */
    fun findMatches(editable: Editable): Sequence<MatchResult>

}