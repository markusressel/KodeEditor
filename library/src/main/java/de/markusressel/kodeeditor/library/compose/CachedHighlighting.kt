package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.util.AnnotatedStringHighlighter
import kotlinx.coroutines.runBlocking

/**
 * Highlights the text of the editor. The text field asks for this whenever its value changes, also when only the
 * selection changed, so the result for the last text is kept. Because the result only depends on the text,
 * the rule book and the color scheme, it can not get out of date.
 */
internal class CachedHighlighting(
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
) : VisualTransformation {

    private val highlighter = AnnotatedStringHighlighter(languageRuleBook, colorScheme)
    private var lastText: String? = null
    private var lastResult = AnnotatedString("")

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text != lastText) {
            lastResult = runBlocking { highlighter.highlight(text.text) }
            lastText = text.text
        }
        return TransformedText(lastResult, OffsetMapping.Identity)
    }
}
