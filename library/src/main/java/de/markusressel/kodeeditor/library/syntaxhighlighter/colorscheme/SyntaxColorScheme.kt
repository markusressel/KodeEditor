package de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme

import android.text.style.CharacterStyle

/**
 * A color scheme for a syntax highlighter
 */
interface SyntaxColorScheme {

    /**
     * Get a set of styles to apply for a specific text/section type
     */
    fun getStyles(type: SectionTypeEnum): Set<() -> CharacterStyle>

}
