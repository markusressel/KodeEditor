package de.markusressel.kodeeditor.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme

open class AnnotatedStringHighlighter(
        private val languageRuleBook: LanguageRuleBook,
        private val colorScheme: ColorScheme = languageRuleBook.defaultColorScheme
) : LanguageRuleBook by languageRuleBook {

    suspend fun highlight(text: String): AnnotatedString {
        val ruleMatches = createHighlighting(text)

        val styles: List<AnnotatedString.Range<SpanStyle>> = ruleMatches.map { (rule, matches) ->
            matches.map { (start, end) ->
                colorScheme.getStyles(rule).map {
                    // TODO: just for testing
                    SpanStyle(
                            color = Color.Green
                    )
                }.map {
                    AnnotatedString.Range(it, start = start, end = end)
                }
            }.flatten()
        }.flatten()

        return AnnotatedString(text, spanStyles = styles)
    }

}