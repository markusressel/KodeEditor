package de.markusressel.kodeeditor.library.syntaxhighlighter.markdown.rule

import android.text.Editable
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SectionTypeEnum

class HeadingRule : HighlighterRuleBase() {
    override fun getSectionType(): SectionTypeEnum {
        return SectionTypeEnum
                .Heading
    }

    override fun findMatches(editable: Editable): Sequence<MatchResult> {
        return PATTERN
                .findAll(editable)
    }

    companion object {
        val PATTERN = "#{1,6} .*"
                .toRegex()
    }

}