package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
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
import org.junit.Assert.assertFalse
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

    @Test
    fun `range styles win over the placeholder of a collapsed part they cover`() {
        val state = KodeEditorState("ab[link](url)cd")
        val collapsed = CollapsedRanges(intArrayOf(2), intArrayOf(13), arrayOf("link"))
        val placeholder = SpanStyle(color = Color.Gray)
        val result = lineTexts(
            state, 0..0, collapsed = collapsed, placeholderStyle = placeholder, rangeStyles = listOf(range(1, 15)),
        ).single()
        assertEquals("ablinkcd", result.text)
        assertEquals(
            listOf(AnnotatedString.Range(placeholder, 2, 6), AnnotatedString.Range(hidden, 1, 8)),
            result.spanStyles,
        )
    }

    @Test
    fun `a collapsed part is found from any position within it`() {
        val collapsed = CollapsedRanges(intArrayOf(2, 20), intArrayOf(13, 25), arrayOf("a", "b"))
        assertEquals(-1, collapsed.containing(1))
        assertEquals(0, collapsed.containing(2))
        assertEquals(0, collapsed.containing(12))
        assertEquals(-1, collapsed.containing(13))
        assertEquals(1, collapsed.containing(24))
        assertEquals(-1, collapsed.containing(25))
    }

    @Test
    fun `alphas are cut to the text and move with it like the styles`() {
        val state = KodeEditorState("hello world")
        state.setRangeAlphas(listOf(AnnotatedString.Range(0.5f, 6, 99), AnnotatedString.Range(0.2f, 3, 3)))
        assertEquals(listOf(AnnotatedString.Range(0.5f, 6, 11)), state.rangeAlphas)
        state.applyRemote(listOf(TextSplice(0, 0, ">")))
        assertEquals(listOf(AnnotatedString.Range(0.5f, 7, 12)), state.rangeAlphas)
        state.applyRemote(listOf(TextSplice(5, 7, "")))
        assertEquals(emptyList<AnnotatedString.Range<Float>>(), state.rangeAlphas)
    }

    private fun KodeEditorState.appearingRanges() = rangeAlphas.map { it.start to it.end }

    @Test
    fun `nothing appears while no editor shows the state with an appearance`() {
        val state = KodeEditorState("hello world")
        state.appear(0, 5)
        state.applyRemote(listOf(TextSplice(0, 0, "ab")), appear = true)
        assertTrue(state.rangeAlphas.isEmpty())
    }

    @Test
    fun `only the last characters of text that appears piece after piece appear, per tag`() {
        val state = KodeEditorState("abcdefghij")
        state.appearance = KodeEditorAppearance(window = 4)
        state.appear(0, 2, "agent")
        state.appear(2, 4, "agent")
        state.appear(8, 10, "jane")
        assertEquals(listOf(0 to 2, 2 to 4, 8 to 10), state.appearingRanges())
        state.appear(4, 6, "agent")
        assertEquals("the first piece left the window", listOf(2 to 4, 8 to 10, 4 to 6), state.appearingRanges())
    }

    @Test
    fun `there are never more appearing ranges than allowed, the oldest is shown at once`() {
        val state = KodeEditorState("abcdefghij")
        state.appearance = KodeEditorAppearance(maxRanges = 2)
        state.appear(0, 1)
        state.appear(3, 4)
        state.appear(6, 7)
        assertEquals(listOf(3 to 4, 6 to 7), state.appearingRanges())
    }

    @Test
    fun `the application decides which text stops appearing, typing does not`() {
        val state = KodeEditorState("abcdefghij")
        state.appearance = KodeEditorAppearance()
        state.appear(0, 2, "agent")
        state.applyRemote(listOf(TextSplice(5, 0, "xy")), appear = true, appearTag = "jane")
        state.setSelection(TextRange(10))
        state.replaceSelection("!")
        state.undo()
        assertEquals(listOf(0 to 2, 5 to 7), state.appearingRanges())
        state.finishAppearing("agent")
        assertEquals(listOf(5 to 7), state.appearingRanges())
        state.finishAppearing()
        assertTrue(state.rangeAlphas.isEmpty())
    }

    @Test
    fun `appearing text gets more visible with each frame until it is shown`() {
        val state = KodeEditorState("abcdefghij")
        state.appearance = KodeEditorAppearance()
        state.appear(0, 2)
        assertTrue(state.onAppearFrame(1000, 100))
        assertEquals(0f, state.rangeAlphas.single().item)
        assertTrue(state.onAppearFrame(1050, 100))
        assertEquals(0.75f, state.rangeAlphas.single().item, 0.001f)
        assertFalse(state.onAppearFrame(1100, 100))
        assertTrue(state.rangeAlphas.isEmpty())
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

        state.setRangeAlphas(listOf(AnnotatedString.Range(0f, 0, state.text.length)))
        composeRule.waitForIdle()
        assertEquals("an alpha of 0 draws nothing", empty, drawnPixels())

        state.setRangeAlphas(listOf(AnnotatedString.Range(0f, 5, 9)))
        composeRule.waitForIdle()
        assertEquals("an alpha only hides its own range", firstLine, drawnPixels())

        state.setRangeAlphas(listOf(AnnotatedString.Range(0.5f, 5, 9)))
        composeRule.waitForIdle()
        val half = drawnPixels()
        assertTrue("half of the alpha draws the range lighter", half > firstLine)
    }

    @Test
    fun `text appears with the frames of the editor, and the frames stop when it is shown`() {
        val state = KodeEditorState("hello")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(300.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                readOnly = true,
                animations = KodeEditorAnimations(appearance = KodeEditorAppearance(durationMs = 200)),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            state.applyRemote(listOf(TextSplice(5, 0, " world")), appear = true)
            // a paused clock sends no notifications about changed state on its own
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(100)
        val alpha = state.rangeAlphas.single().item
        assertTrue("half way, $alpha", alpha > 0f && alpha < 1f)
        composeRule.mainClock.advanceTimeBy(200)
        assertTrue(state.rangeAlphas.isEmpty())
    }
}
