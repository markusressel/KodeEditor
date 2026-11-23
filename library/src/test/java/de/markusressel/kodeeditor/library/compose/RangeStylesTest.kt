package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class RangeStylesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val hidden = SpanStyle(color = Color.Transparent)

    private fun range(start: Int, end: Int, tag: String = "") = AnnotatedString.Range(hidden, start, end, tag)

    private fun stateWith(text: String, vararg ranges: AnnotatedString.Range<SpanStyle>) =
        KodeEditorState(text).apply { setRangeStyles(ranges.toList()) }

    private val KodeEditorState.spans get() = rangeStyles.map { it.start to it.end }

    @Test
    fun `ranges are cut to the text and empty ones are left out`() {
        val state = stateWith("hello", range(2, 99), range(3, 3), range(7, 9))
        assertEquals(listOf(2 to 5), state.spans)
    }

    @Test
    fun `text inserted in front of a range moves it`() {
        val state = stateWith("hello world", range(6, 11, "a"))
        state.applyRemote(listOf(TextSplice(0, 0, ">> ")))
        assertEquals(listOf(9 to 14), state.spans)
        assertEquals("a", state.rangeStyles.single().tag)
    }

    @Test
    fun `text inserted inside a range becomes a part of it`() {
        val state = stateWith("hello world", range(6, 11))
        state.applyRemote(listOf(TextSplice(8, 0, "XY")))
        assertEquals(listOf(6 to 13), state.spans)
    }

    @Test
    fun `text inserted at the start or the end of a range stays out of it`() {
        val state = stateWith("hello world", range(6, 11))
        state.setSelection(TextRange(11))
        state.replaceSelection("!")
        assertEquals(listOf(6 to 11), state.spans)
        state.applyRemote(listOf(TextSplice(6, 0, "big ")))
        assertEquals(listOf(10 to 15), state.spans)
        assertEquals("world", state.text.substring(10, 15))
    }

    @Test
    fun `removed text shrinks a range, and a range that is removed completely disappears`() {
        val state = stateWith("hello world", range(2, 8), range(9, 11))
        state.applyRemote(listOf(TextSplice(0, 4, "")))
        assertEquals(listOf(0 to 4, 5 to 7), state.spans)
        state.applyRemote(listOf(TextSplice(4, 3, "")))
        assertEquals(listOf(0 to 4), state.spans)
    }

    @Test
    fun `a range in front of a change stays the same list`() {
        val state = stateWith("hello world", range(0, 3))
        val before = state.rangeStyles
        state.applyRemote(listOf(TextSplice(5, 0, ",")))
        assertSame(before, state.rangeStyles)
    }

    @Test
    fun `ranges move with each change of the whole text, with undo and redo`() {
        val state = stateWith("hello world", range(6, 11))
        state.setText("oh, hello world")
        assertEquals(listOf(10 to 15), state.spans)
        state.setSelection(TextRange(0))
        state.replaceSelection("x")
        state.undo()
        assertEquals(listOf(10 to 15), state.spans)
    }

    @Test
    fun `a listener can clear the ranges when the user types`() {
        val state = stateWith("hello world", range(6, 11))
        state.addListener(object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) = state.setRangeStyles(emptyList())
        })
        state.applyRemote(listOf(TextSplice(0, 0, "a")))
        assertEquals(listOf(7 to 12), state.spans)
        state.setSelection(TextRange(0))
        state.replaceSelection("b")
        assertEquals(emptyList<Pair<Int, Int>>(), state.spans)
    }

    @Test
    fun `range styles win over the highlighting and the composition`() {
        val red = SpanStyle(color = Color.Red)
        val state = KodeEditorState("one\ntwo")
        val result = lineTexts(
            state, 0..1, listOf(AnnotatedString.Range(red, 0, 7)), TextRange(4, 7),
            rangeStyles = listOf(range(2, 5)),
        )
        assertEquals(
            listOf(AnnotatedString.Range(red, 0, 3), AnnotatedString.Range(hidden, 2, 3)),
            result[0].spanStyles,
        )
        assertEquals(
            listOf(
                AnnotatedString.Range(red, 0, 3),
                AnnotatedString.Range(SpanStyle(textDecoration = TextDecoration.Underline), 0, 3),
                AnnotatedString.Range(hidden, 0, 1),
            ),
            result[1].spanStyles,
        )
    }

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    /** how many pixels of the editor are not the color of its background */
    private fun drawnPixels(): Int {
        val image = composeRule.onNodeWithTag("editor").captureToImage().toPixelMap()
        val background = image[image.width - 1, image.height - 1]
        var count = 0
        for (x in 0 until image.width) for (y in 0 until image.height) if (image[x, y] != background) count++
        return count
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a transparent range is not drawn, in a read only editor too`() {
        val state = KodeEditorState("")
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(300.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                textStyle = TextStyle(fontSize = 30.sp),
                readOnly = true,
                gutterColumns = emptyList(),
                scrollbars = false,
            )
        }
        composeRule.waitForIdle()
        val empty = drawnPixels()

        // the text arrives hidden, in the same message, so no frame shows it
        state.applyRemote(listOf(TextSplice(0, 0, "MMMM\nWWWW")))
        state.setRangeStyles(listOf(range(0, state.text.length)))
        composeRule.waitForIdle()
        assertEquals(empty, drawnPixels())

        state.setRangeStyles(listOf(range(5, 9)))
        composeRule.waitForIdle()
        val firstLine = drawnPixels()
        assertTrue("the first line is drawn", firstLine > empty)

        state.setRangeStyles(emptyList())
        composeRule.waitForIdle()
        assertTrue("both lines are drawn", drawnPixels() > firstLine)
        assertEquals("the text is unchanged", "MMMM\nWWWW", state.text)
    }
}
