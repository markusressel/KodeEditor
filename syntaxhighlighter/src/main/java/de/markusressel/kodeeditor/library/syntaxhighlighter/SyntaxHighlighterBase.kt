package de.markusressel.kodeeditor.library.syntaxhighlighter

import android.text.style.CharacterStyle
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SyntaxColorScheme

abstract class SyntaxHighlighterBase : SyntaxHighlighter {

    override val appliedStyles: MutableSet<CharacterStyle> = mutableSetOf()

    override var colorScheme: SyntaxColorScheme = getDefaultColorScheme()

}