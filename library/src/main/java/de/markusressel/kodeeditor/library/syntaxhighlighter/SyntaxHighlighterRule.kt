package de.markusressel.kodeeditor.library.syntaxhighlighter

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SectionTypeEnum

interface SyntaxHighlighterRule {

    /**
     * Get the type of section this rule is meant for
     */
    fun getSectionType(): SectionTypeEnum

    /**
     * Find segments in the editable that are affected by this rule
     */
    fun findMatches(editable: Editable): Sequence<MatchResult>

}