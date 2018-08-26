package de.markusressel.kodeeditor.library.java.colorscheme

import android.graphics.Color
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import de.markusressel.kodeeditor.library.java.rule.*
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxColorScheme
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighterRule
import java.util.Collections.emptySet

/**
 * A dark color scheme for markdown text
 */
class DarkBackgroundColorScheme : SyntaxColorScheme {

    override fun getStyles(type: SyntaxHighlighterRule): Set<() -> CharacterStyle> {
        return when (type) {
            is ImportKeywordRule, is PackageKeywordRule, is ClassKeywordRule, is TypeKeywordRule, is VisibilityKeywordRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#FF6D00")) })
            }
            is AnnotationRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#FBC02D")) })
            }
            is CommentRule -> {
                setOf({ ForegroundColorSpan(Color.parseColor("#33691E")) })
            }
            else -> emptySet()
        }
    }

}