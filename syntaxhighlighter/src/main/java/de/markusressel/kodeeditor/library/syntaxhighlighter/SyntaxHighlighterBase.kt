package de.markusressel.kodeeditor.library.syntaxhighlighter

import android.text.style.CharacterStyle
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SyntaxColorScheme

/**
 * Convenience base class for implementing a syntax highlighter
 */
abstract class SyntaxHighlighterBase : SyntaxHighlighter {

    override val appliedStyles: MutableSet<CharacterStyle> = mutableSetOf()

    override var colorScheme: SyntaxColorScheme = getDefaultColorScheme()

}