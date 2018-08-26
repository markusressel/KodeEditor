package de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme

import android.text.style.CharacterStyle
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule

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
