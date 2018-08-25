package de.markusressel.kodeeditor.library.syntaxhighlighter.markdown.colorscheme

import android.graphics.Color
import android.graphics.Typeface
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SectionTypeEnum
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SyntaxColorScheme

/**
 * A dark color scheme for markdown text
 */
class DarkBackgroundColorScheme : SyntaxColorScheme {

    override fun getStyles(type: SectionTypeEnum): Set<() -> CharacterStyle> {
        return when (type) {
            SectionTypeEnum.BoldText -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#0091EA")) }, { StyleSpan(Typeface.BOLD) })
            }
            SectionTypeEnum.ItalicText -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#0091EA")) }, { StyleSpan(Typeface.ITALIC) })
            }
            SectionTypeEnum.SourceCode -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#00C853")) })
            }
            SectionTypeEnum.Heading -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#FF6D00")) })
            }
            SectionTypeEnum.Link -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#7C4DFF")) })
            }
            SectionTypeEnum.StrikedText -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#5D4037")) })
            }
            else -> emptySet()
        }
    }

}