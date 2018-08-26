package de.markusressel.kodeeditor.library.markdown.colorscheme

import android.graphics.Color
import android.graphics.Typeface
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import de.markusressel.kodeeditor.library.markdown.rule.*
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule
import de.markusressel.kodeeditor.library.syntaxhighlighter.colorscheme.SyntaxColorScheme

/**
 * A dark color scheme for markdown text
 */
class DarkBackgroundColorScheme : SyntaxColorScheme {

    override fun getStyles(type: SyntaxHighlighterRule): Set<() -> CharacterStyle> {
        return when (type) {
            is BoldRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#0091EA")) }, { StyleSpan(Typeface.BOLD) })
            }
            is ItalicRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#0091EA")) }, { StyleSpan(Typeface.ITALIC) })
            }
            is CodeInlineRule, is CodeLineRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#00C853")) })
            }
            is HeadingRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#FF6D00")) })
            }
            is ImageLinkRule, is TextLinkRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#7C4DFF")) })
            }
            is StrikeRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#5D4037")) })
            }
            else -> emptySet()
        }
    }

}