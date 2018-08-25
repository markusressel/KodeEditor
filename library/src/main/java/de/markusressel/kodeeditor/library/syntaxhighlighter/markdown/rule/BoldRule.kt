package de.markusressel.kodeeditor.library.syntaxhighlighter.markdown.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SectionTypeEnum

class BoldRule : HighlighterRuleBase() {

    override fun getSectionType(): SectionTypeEnum {
        return SectionTypeEnum
                .BoldText
    }

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "\\*{2}(.+?)\\*{2}"
                .toRegex()
    }

}