package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import de.markusressel.kodehighlighter.core.colorscheme.GitHubDarkTheme
import de.markusressel.kodehighlighter.core.colorscheme.GitHubLightTheme
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** code blocks of Markdown in their language, with the default rule book of KodeHighlighter */
class MarkdownCodeBlockTest {
    private val markdownColors = DarkBackgroundColorSchemeWithSpanStyle()
    private val heading = Color(0xFFFF6D00)
    private val code = Color(0xFF00C853)

    private fun highlighting(text: String, dark: Boolean = true): Pair<KodeEditorState, EditorHighlighting> {
        val state = KodeEditorState(text)
        val highlighting = EditorHighlighting(
            state = state,
            languageRuleBook = MarkdownRuleBook(),
            colorScheme = markdownColors.withEmbeddedFallback(dark),
            firstRunLimitMs = 10_000,
        )
        return state to highlighting
    }

    private fun List<AnnotatedString.Range<SpanStyle>>.colorsAt(position: Int) =
        filter { position >= it.start && position < it.end }.map { it.item.color }

    private val darkColors = GitHubDarkTheme.styles.values.map { Color(it.color) }
    private val lightColors = GitHubLightTheme.styles.values.map { Color(it.color) }

    @Test
    fun aCodeBlockGetsTheColorsOfItsLanguageFromTheThemeThatFitsTheBackground() {
        val text = "# Title\n```kotlin\nfun main() {}\n```\n"
        val dark = highlighting(text, dark = true).second.styles
        val light = highlighting(text, dark = false).second.styles
        val keyword = text.indexOf("fun")

        assertEquals(listOf(heading), dark.colorsAt(0))
        assertEquals(listOf(heading), light.colorsAt(0))
        assertEquals(listOf(code), dark.colorsAt(text.indexOf("```")))
        val darkKeyword = dark.colorsAt(keyword).single()
        val lightKeyword = light.colorsAt(keyword).single()
        assertTrue(darkKeyword in darkColors)
        assertTrue(lightKeyword in lightColors)
        assertNotEquals(darkKeyword, lightKeyword)
    }

    /** waits for the highlighting of the current text */
    private fun EditorHighlighting.settle(state: KodeEditorState, change: () -> Unit) = runBlocking {
        val updating = launch(Dispatchers.Default) { keepUpdated() }
        val before = styles
        change()
        withTimeout(5000) { while (styles === before) delay(10) }
        updating.cancel()
        styles
    }

    @Test
    fun editingTheFenceLinesHighlightsTheBlockAgain() {
        val (state, highlighting) = highlighting("```kotlin\nfun x\n```\n# a\n")

        fun colorsOf(styles: List<AnnotatedString.Range<SpanStyle>>, word: String, after: Int = 0) =
            styles.colorsAt(state.rawText.indexOf(word, after))

        assertTrue(colorsOf(highlighting.styles, "fun").single() in darkColors)
        assertEquals(listOf(heading), colorsOf(highlighting.styles, "# a"))

        // another language without rules: the whole block is code
        var styles = highlighting.settle(state) { state.setText("```text\nfun x\n```\n# a\n") }
        assertEquals(listOf(code), colorsOf(styles, "fun"))
        assertEquals(listOf(heading), colorsOf(styles, "# a"))

        // back to Kotlin
        styles = highlighting.settle(state) { state.setText("```kotlin\nfun x\n```\n# a\n") }
        assertTrue(colorsOf(styles, "fun").single() in darkColors)

        // without the closing fence, the block reaches the end and the heading is Kotlin now
        styles = highlighting.settle(state) { state.setText("```kotlin\nfun x\n# a\nfun y\n") }
        assertTrue(colorsOf(styles, "fun", after = 5).single() in darkColors)
        assertTrue(heading !in colorsOf(styles, "# a"))
    }
}
