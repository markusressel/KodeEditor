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
 *
 * The text field only gets the styles that change the size of the text, which it needs to position things correctly.
 * Android looks through all styles of a text for each of its lines during layout, so any style that is not needed
 * there makes each keystroke slower. All of them are drawn by [VisibleLines].
 */
internal class CachedHighlighting(
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
) : VisualTransformation {

    private val highlighter = AnnotatedStringHighlighter(languageRuleBook, colorScheme)
    private var lastText: String? = null
    private var lastHighlighted = AnnotatedString("")
    private var lastForLayout = AnnotatedString("")
    private var previousText: String? = null
    private var previousHighlighted = AnnotatedString("")

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text != lastText) {
            previousText = lastText
            previousHighlighted = lastHighlighted
            lastHighlighted = runBlocking { highlighter.highlight(text.text) }
            lastForLayout = AnnotatedString(
                text = text.text,
                spanStyles = lastHighlighted.spanStyles.filter { it.item.changesTextSize() },
            )
            lastText = text.text
        }
        return TransformedText(lastForLayout, OffsetMapping.Identity)
    }

    /**
     * The layout of a new text arrives after its highlighting, and until then the previous layout can still be
     * drawn. So the styles of the previous text are kept too.
     *
     * @param text the text to get the styles for
     * @return all styles of [text], empty if it is neither the last nor the previous highlighted text
     */
    fun stylesOf(text: String): List<AnnotatedString.Range<SpanStyle>> {
        return when (text) {
            lastText -> lastHighlighted.spanStyles
            previousText -> previousHighlighted.spanStyles
            else -> emptyList()
        }
    }
}

/**
 * Color, background, shadow and decoration can be left out of the layout, anything else can change the size of the text
 */
internal fun SpanStyle.changesTextSize(): Boolean {
    return this != SpanStyle(color = color, background = background, shadow = shadow, textDecoration = textDecoration)
}
