package de.markusressel.kodeeditor.library.syntaxhighlighter

import android.text.style.CharacterStyle

/**
 * A color scheme for a syntax highlighter.
 * This essentially maps rules to the styles that will be applied to matched text passages.
 */
interface SyntaxColorScheme {

    /**
     * Get a set of styles to apply for a specific text/section type
     */
    fun getStyles(type: SyntaxHighlighterRule): Set<() -> CharacterStyle>

}
