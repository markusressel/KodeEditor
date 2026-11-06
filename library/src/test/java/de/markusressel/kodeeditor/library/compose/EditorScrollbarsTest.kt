package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

class ScrollbarMathTest {

    @Test
    fun `the thumb shows the visible part of the content`() {
        // a quarter of the content is visible
        assertEquals(25f, ScrollbarMath.thumbLength(track = 100f, visible = 10f, maxOffset = 30f, minLength = 5f))
    }

    @Test
    fun `the thumb does not get too small to hit`() {
        assertEquals(20f, ScrollbarMath.thumbLength(track = 100f, visible = 1f, maxOffset = 1000f, minLength = 20f))
    }

    @Test
    fun `there is no thumb without anything to scroll`() {
        assertEquals(0f, ScrollbarMath.thumbLength(track = 100f, visible = 10f, maxOffset = 0f, minLength = 5f))
    }

    @Test
    fun `the thumb reaches the end of the track at the end of the content`() {
        assertEquals(0f, ScrollbarMath.thumbStart(track = 100f, length = 25f, offset = 0f, maxOffset = 30f))
        assertEquals(75f, ScrollbarMath.thumbStart(track = 100f, length = 25f, offset = 30f, maxOffset = 30f))
    }

    @Test
    fun `moving the thumb over its free track moves over the whole content`() {
        assertEquals(30f, ScrollbarMath.offsetFor(0f, moved = 75f, track = 100f, length = 25f, maxOffset = 30f))
        assertEquals(15f, ScrollbarMath.offsetFor(0f, moved = 37.5f, track = 100f, length = 25f, maxOffset = 30f))
        assertEquals("not beyond the end", 30f, ScrollbarMath.offsetFor(20f, 500f, 100f, 25f, 30f))
        assertEquals("not before the start", 0f, ScrollbarMath.offsetFor(20f, -500f, 100f, 25f, 30f))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class EditorScrollbarsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private val defaults = KodeEditorScrollbarStyle()
    private val viewState = KodeEditorViewState()
    private var movedView = 0

    private val longText = (0 until 1000).joinToString("\n") { "line $it" }

    private fun show(
        state: KodeEditorState,
        lineWrapping: Boolean = false,
        scrollbars: Boolean = true,
        scrollbarStyle: KodeEditorScrollbarStyle = defaults,
    ) {
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                readOnly = true,
                gutterColumns = emptyList(),
                lineWrapping = lineWrapping,
                viewState = viewState,
                onUserMovedView = { movedView++ },
                scrollbars = scrollbars,
                scrollbarStyle = scrollbarStyle,
            )
        }
        composeRule.waitForIdle()
    }

    /** a hidden thumb is placed outside the editor */
    private fun isShown(tag: String): Boolean {
        if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) return false
        return composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().left.value >= 0f
    }

    /** one frame at a time, with the layout of each frame, which a paused clock otherwise leaves for later */
    private fun advanceFrames(millis: Long) {
        val end = composeRule.mainClock.currentTime + millis
        while (composeRule.mainClock.currentTime < end) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    /** pans the text, and stops before the bars fade out again */
    private fun pan(swipe: TouchInjectionScope.() -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("editor").performTouchInput(swipe)
        composeRule.mainClock.advanceTimeBy(100)
    }

    @Test
    fun `the bars are hidden until the view moves`() {
        show(KodeEditorState(longText))

        assertFalse(isShown(VERTICAL_SCROLLBAR_TAG))
        pan { swipeUp() }
        assertTrue(isShown(VERTICAL_SCROLLBAR_TAG))
    }

    @Test
    fun `the bars fade out after the view stopped`() {
        show(KodeEditorState(longText))
        pan { swipeUp() }

        composeRule.mainClock.advanceTimeBy(defaults.hideDelayMillis + 5000)

        assertFalse(isShown(VERTICAL_SCROLLBAR_TAG))
    }

    @Test
    fun `the bars stay as long as the style says`() {
        val style = KodeEditorScrollbarStyle(hideDelayMillis = 5000L, fadeOutMillis = 0)
        show(KodeEditorState(longText), scrollbarStyle = style)
        pan { swipeUp() }
        // a held thumb stops the fling, so the bars wait from its release
        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { up() }

        advanceFrames(4000)
        assertTrue("longer than the default", isShown(VERTICAL_SCROLLBAR_TAG))

        advanceFrames(1100)
        assertFalse(isShown(VERTICAL_SCROLLBAR_TAG))
    }

    @Test
    fun `the bars stay in place while they fade out`() {
        show(KodeEditorState(longText))
        pan { swipeUp() }
        // a held thumb stops the fling, so the bars fade from its release
        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { up() }

        advanceFrames(defaults.hideDelayMillis + defaults.fadeOutMillis / 2)
        assertTrue("still fading", isShown(VERTICAL_SCROLLBAR_TAG))

        advanceFrames(defaults.fadeOutMillis.toLong())
        assertFalse(isShown(VERTICAL_SCROLLBAR_TAG))
    }

    @Test
    fun `the bars stay while a finger rests on the text after a pan`() {
        show(KodeEditorState(longText))
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("editor").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -50f)) }
        }

        advanceFrames(defaults.hideDelayMillis + defaults.fadeOutMillis + 1000)
        assertTrue("the finger still holds the view", isShown(VERTICAL_SCROLLBAR_TAG))

        // the finger rested, so it does not fling
        composeRule.onNodeWithTag("editor").performTouchInput {
            advanceEventTime(1000)
            up()
        }
        advanceFrames(defaults.hideDelayMillis + defaults.fadeOutMillis + 100)
        assertFalse(isShown(VERTICAL_SCROLLBAR_TAG))
    }

    @Test
    fun `dragging the thumb to the bottom shows the end of the text`() {
        val state = KodeEditorState(longText)
        show(state)
        pan { swipeUp() }

        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 200f)) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(100)

        assertTrue(viewState.isPositionVisible(state.rawText.length))
        assertTrue("the drag moved the view by hand", movedView >= 2)
        assertFalse(viewState.isUserMovingView)
    }

    @Test
    fun `the view follows the thumb back up`() {
        val state = KodeEditorState(longText)
        show(state)
        pan { swipeUp() }

        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 200f)) }
            repeat(40) { moveBy(Offset(0f, -200f)) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(100)

        assertTrue(viewState.isPositionVisible(0))
    }

    @Test
    fun `a held thumb moves the view by hand`() {
        show(KodeEditorState(longText))
        pan { swipeUp() }

        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(100)
        assertTrue(viewState.isUserMovingView)
        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(100)
        assertFalse(viewState.isUserMovingView)
    }

    @Test
    fun `holding the text still next to the thumb selects a word`() {
        val state = KodeEditorState(longText)
        show(state)
        pan { swipeUp() }

        composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(10f, centerY)) }
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.onNodeWithTag("editor").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(100)

        assertFalse(state.selection.collapsed)
    }

    @Test
    fun `holding the thumb still does not select text`() {
        val state = KodeEditorState(longText)
        show(state)
        pan { swipeUp() }
        val before = state.selection

        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.onNodeWithTag(VERTICAL_SCROLLBAR_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(100)

        assertEquals(before, state.selection)
        assertTrue("the held bar stays a moment after it is released", isShown(VERTICAL_SCROLLBAR_TAG))
    }

    @Test
    fun `a short text has no bars`() {
        show(KodeEditorState("short"))
        pan { swipeUp() }

        assertFalse(isShown(VERTICAL_SCROLLBAR_TAG))
        assertFalse(isShown(HORIZONTAL_SCROLLBAR_TAG))
    }

    @Test
    fun `a long line has a bar at the bottom`() {
        val state = KodeEditorState("x".repeat(2000) + "\n" + longText)
        show(state)
        pan { swipeLeft() }

        assertTrue(isShown(HORIZONTAL_SCROLLBAR_TAG))
    }

    @Test
    fun `wrapped lines need no bar at the bottom`() {
        val state = KodeEditorState("x".repeat(2000) + "\n" + longText)
        show(state, lineWrapping = true)
        pan { swipeUp() }

        assertTrue(isShown(VERTICAL_SCROLLBAR_TAG))
        assertFalse(isShown(HORIZONTAL_SCROLLBAR_TAG))
    }

    @Test
    fun `the bars can be turned off`() {
        show(KodeEditorState(longText), scrollbars = false)
        pan { swipeUp() }

        assertFalse(isShown(VERTICAL_SCROLLBAR_TAG))
    }

    @Test
    fun `a tap on the text below a hidden bar still moves the cursor`() {
        val state = KodeEditorState(longText)
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                gutterColumns = emptyList(),
                viewState = viewState,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor").performTouchInput { click(Offset(right - 5f, 5f)) }
        composeRule.waitForIdle()

        assertEquals(TextRange(state.lineEnd(0)), state.selection)
    }
}
