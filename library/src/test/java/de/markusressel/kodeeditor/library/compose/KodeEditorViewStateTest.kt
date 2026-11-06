package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class KodeEditorViewStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private val viewState = KodeEditorViewState()
    private lateinit var scope: CoroutineScope
    private var layout: KodeEditorLayout? = null
    private var movedView = 0

    private val longText = (0 until 1000).joinToString("\n") { "line $it" }

    private fun show(
        state: KodeEditorState,
        animations: KodeEditorAnimations = KodeEditorAnimations(),
        readOnly: Boolean = false,
        lineWrapping: Boolean = false,
        columns: List<KodeEditorGutterColumn> = emptyList(),
        foldProvider: KodeEditorFoldProvider? = null,
    ) {
        composeRule.setContent {
            scope = rememberCoroutineScope()
            KodeEditor(
                modifier = Modifier.size(400.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                readOnly = readOnly,
                onTextLayout = { layout = it },
                gutterColumns = columns,
                foldProvider = foldProvider,
                animations = animations,
                lineWrapping = lineWrapping,
                viewState = viewState,
                onUserMovedView = { movedView++ },
            )
        }
        composeRule.waitForIdle()
    }

    private fun scroll(block: suspend KodeEditorViewState.() -> Boolean): () -> Boolean? {
        var result: Boolean? = null
        composeRule.runOnIdle { scope.launch { result = viewState.block() } }
        return { result }
    }

    @Test
    fun `nothing is visible before the first layout`() {
        assertNull(viewState.visibleRange)
        assertFalse(viewState.isPositionVisible(0))
        assertFalse(viewState.isUserMovingView)
    }

    @Test
    fun `a jump shows the position and leaves the cursor and the focus alone`() {
        val state = KodeEditorState(longText)
        state.setSelection(TextRange(3))
        show(state, readOnly = true)
        val position = state.lineStart(500)
        assertFalse(viewState.isPositionVisible(position))

        val result = scroll { scrollToPosition(position, animate = false) }
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertTrue(viewState.isPositionVisible(position))
        assertFalse(viewState.isPositionVisible(0))
        assertEquals(TextRange(3), state.selection)
        assertEquals("nothing moved the view by hand", 0, movedView)
    }

    @Test
    fun `an animated scroll gets there on its own`() {
        val state = KodeEditorState(longText)
        show(state)
        val position = state.lineStart(30)

        val result = scroll { scrollToPosition(position) }
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertTrue(viewState.isPositionVisible(position))
        assertEquals(0, movedView)
    }

    @Test
    fun `a long way also gets there`() {
        val state = KodeEditorState(longText)
        show(state)
        val position = state.lineStart(900)

        val result = scroll { scrollToPosition(position) }
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertTrue(viewState.isPositionVisible(position))
    }

    @Test
    fun `without the scrolling animation it jumps`() {
        val state = KodeEditorState(longText)
        show(state, animations = KodeEditorAnimations.None)
        composeRule.mainClock.autoAdvance = false
        val position = state.lineStart(500)

        scroll { scrollToPosition(position) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertTrue(viewState.isPositionVisible(position))
    }

    @Test
    fun `a visible position does not move the view`() {
        val state = KodeEditorState(longText)
        show(state)
        val before = viewState.visibleRange

        val result = scroll { scrollToPosition(state.lineStart(3)) }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertEquals(before, viewState.visibleRange)
    }

    @Test
    fun `a position behind the end means the end`() {
        val state = KodeEditorState(longText)
        show(state)

        val result = scroll { scrollToPosition(Int.MAX_VALUE, animate = false) }
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertTrue(viewState.isPositionVisible(state.rawText.length))
    }

    private fun topOfView(): Float = viewState.viewport!!.offset.y

    @Test
    fun `a line goes exactly to the top`() {
        val state = KodeEditorState(longText)
        state.setSelection(TextRange(3))
        show(state, readOnly = true)
        val layout = layout!!

        val result = scroll { scrollToLine(500, fraction = 0.25f) }
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertEquals(layout.getLineTop(500) + layout.lineHeight / 4, topOfView(), 0.01f)
        assertEquals(state.lineStart(500), viewState.visibleRange?.start)
        assertEquals(TextRange(3), state.selection)
        assertEquals("nothing moved the view by hand", 0, movedView)
    }

    @Test
    fun `a small step moves the view too`() {
        show(KodeEditorState(longText))
        val layout = layout!!
        scroll { scrollToLine(500) }
        composeRule.waitForIdle()

        // far less than a tenth of the view, which scrollToRange would ignore
        scroll { scrollToLine(500, fraction = 0.1f) }
        composeRule.waitForIdle()

        assertEquals(layout.getLineTop(500) + layout.lineHeight / 10, topOfView(), 0.01f)
    }

    @Test
    fun `a visible line still goes to the top`() {
        show(KodeEditorState(longText))
        val layout = layout!!

        scroll { scrollToLine(3) }
        composeRule.waitForIdle()

        assertEquals(layout.getLineTop(3), topOfView(), 0.01f)
    }

    @Test
    fun `the fraction is of all rows of a wrapped line`() {
        val text = "first\n" + "word ".repeat(400) + "\n" + longText
        show(KodeEditorState(text), lineWrapping = true)
        val layout = layout!!
        val top = layout.getLineTop(1)
        val bottom = layout.getLineBottom(1)
        assertTrue("the line wraps", bottom - top > 2 * layout.lineHeight)

        scroll { scrollToLine(1, fraction = 0.5f) }
        composeRule.waitForIdle()

        assertEquals((top + bottom) / 2, topOfView(), 0.01f)
    }

    @Test
    fun `a line behind the end gets as far as the view can`() {
        val state = KodeEditorState(longText)
        show(state)

        val result = scroll { scrollToLine(Int.MAX_VALUE) }
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertTrue(viewState.isPositionVisible(state.rawText.length))
    }

    @Test
    fun `an animated scroll to a line ends at the top`() {
        show(KodeEditorState(longText))
        val layout = layout!!

        val result = scroll { scrollToLine(40, animate = true) }
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()

        assertEquals(true, result())
        assertEquals(layout.getLineTop(40), topOfView(), 0.5f)
        assertEquals(0, movedView)
    }

    private fun jumpToLine(line: Int, fraction: Float = 0f, viewportFraction: Float = 0f): Boolean? {
        val result = scroll { scrollToLine(line, fraction, viewportFraction) }
        composeRule.waitForIdle()
        return result()
    }

    /** reads each height of the view back, and puts it there again */
    private fun assertRoundTrips() {
        val before = topOfView()
        listOf(0f, 0.3f, 0.5f, 0.75f, 1f).forEach { f ->
            val place = viewState.lineAt(f)!!
            assertFalse("$place is shown", layout!!.isLineHidden(place.line))
            assertEquals(true, jumpToLine(place.line, place.fraction, viewportFraction = f))
            assertEquals("at $f, $place", before, topOfView(), 0.01f)
        }
    }

    @Test
    fun `a place in a line goes to a height of the view`() {
        show(KodeEditorState(longText))
        val layout = layout!!
        val height = viewState.viewport!!.visibleSize.height

        assertEquals(true, jumpToLine(500, fraction = 0.5f, viewportFraction = 0.5f))

        val place = layout.getLineTop(500) + layout.lineHeight / 2
        assertEquals(place - height / 2, topOfView(), 0.01f)
        val read = viewState.lineAt(0.5f)!!
        assertEquals(500, read.line)
        assertEquals(0.5f, read.fraction, 0.001f)
        assertEquals(0, movedView)
    }

    @Test
    fun `the place at a height of the view is exact within a line`() {
        show(KodeEditorState(longText))
        val layout = layout!!
        jumpToLine(500, fraction = 0.3f)

        val top = viewState.lineAt(0f)!!
        assertEquals(500, top.line)
        assertEquals(0.3f, top.fraction, 0.001f)
        val height = viewState.viewport!!.visibleSize.height
        val bottomY = layout.getLineTop(500) + 0.3f * layout.lineHeight + height
        val bottom = viewState.lineAt(1f)!!
        assertEquals(layout.getLineForVerticalPosition(bottomY), bottom.line)
        assertEquals(bottomY, layout.getLineTop(bottom.line) + bottom.fraction * layout.lineHeight, 0.01f)
    }

    @Test
    fun `nothing is read back before the first layout`() {
        assertNull(viewState.lineAt(0f))
        assertNull(viewState.verticalScrollOffset)
        assertNull(viewState.maxVerticalScrollOffset)
    }

    @Test
    fun `a place read back does not move the view`() {
        show(KodeEditorState(longText))
        jumpToLine(321, fraction = 0.37f, viewportFraction = 0.2f)

        assertRoundTrips()
    }

    @Test
    fun `a place read back in wrapped lines does not move the view`() {
        val text = (0 until 200).joinToString("\n") { "line $it " + "word ".repeat(it % 7 * 30) }
        show(KodeEditorState(text), lineWrapping = true)
        val layout = layout!!
        assertTrue("lines wrap", layout.getLineBottom(6) - layout.getLineTop(6) > 2 * layout.lineHeight)
        jumpToLine(76, fraction = 0.4f, viewportFraction = 0.5f)

        val place = viewState.lineAt(0.5f)!!
        assertEquals(76, place.line)
        assertEquals(0.4f, place.fraction, 0.001f)
        assertRoundTrips()
    }

    @Test
    fun `a place read back next to a fold does not move the view`() {
        val text = "# a\n" + (0 until 40).joinToString("\n") { "a $it" } + "\n# b\n" + longText
        show(KodeEditorState(text), foldProvider = MarkdownFoldProvider)
        val layout = layout!!
        // the arrow of the first heading, at the end of the bar
        val y = layout.getLineTop(0) + layout.lineHeight / 2
        composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(10f, y)); up() }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertTrue("folded", layout.isLineHidden(20))

        // a hidden line is at the place of its fold
        jumpToLine(20)
        assertEquals(layout.getLineTop(20), topOfView(), 0.01f)
        assertRoundTrips()

        jumpToLine(41, fraction = 0.5f, viewportFraction = 0.5f)
        assertRoundTrips()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a place read back after a zoom does not move the view`() {
        show(KodeEditorState(longText))
        val before = viewState.maxVerticalScrollOffset!!
        composeRule.onNodeWithTag("editor").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Equals)
            keyUp(Key.CtrlLeft)
        }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        val zoom = viewState.viewport!!.zoom
        assertTrue("zoomed in", zoom > 1f)
        assertTrue("more to scroll on the screen", viewState.maxVerticalScrollOffset!! > before)

        jumpToLine(600, fraction = 0.6f, viewportFraction = 0.4f)
        val read = viewState.lineAt(0.4f)!!
        assertEquals(600, read.line)
        assertEquals(0.6f, read.fraction, 0.001f)
        assertEquals(topOfView() * zoom, viewState.verticalScrollOffset!!, 0.01f)
        assertRoundTrips()
    }

    @Test
    fun `at the start the view only gets as far as it can`() {
        show(KodeEditorState(longText))
        jumpToLine(400)

        assertEquals(true, jumpToLine(2, viewportFraction = 1f))

        assertEquals(0f, topOfView(), 0.01f)
        assertEquals(0f, viewState.verticalScrollOffset!!, 0.01f)
        assertRoundTrips()
    }

    @Test
    fun `at the end the view only gets as far as it can`() {
        val state = KodeEditorState(longText)
        show(state)

        assertEquals(true, jumpToLine(995))

        assertEquals(viewState.maxVerticalScrollOffset!!, viewState.verticalScrollOffset!!, 0.01f)
        assertTrue(viewState.isPositionVisible(state.rawText.length))
        assertEquals(999, viewState.lineAt(1f)!!.line)
        assertRoundTrips()
    }

    @Test
    fun `a newer scroll ends the running one`() {
        val state = KodeEditorState(longText)
        show(state)
        composeRule.mainClock.autoAdvance = false

        val first = scroll { scrollToPosition(state.lineStart(100)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val second = scroll { scrollToPosition(state.lineStart(30)) }
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()

        assertEquals(false, first())
        assertEquals(true, second())
        assertTrue(viewState.isPositionVisible(state.lineStart(30)))
    }

    @Test
    fun `a finger going down ends a scroll without counting as a movement`() {
        val state = KodeEditorState(longText)
        show(state)
        composeRule.mainClock.autoAdvance = false

        val result = scroll { scrollToPosition(state.lineStart(100)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("editor").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertEquals(false, result())
        assertEquals(0, movedView)
        assertFalse(viewState.isUserMovingView)
        composeRule.onNodeWithTag("editor").performTouchInput { up() }
    }

    @Test
    fun `a gesture is reported once and moves the view until it has ended`() {
        val state = KodeEditorState(longText)
        show(state)
        composeRule.mainClock.autoAdvance = false
        val before = viewState.visibleRange

        composeRule.onNodeWithTag("editor").performTouchInput {
            down(center)
            repeat(5) {
                moveBy(Offset(0f, -100f))
                advanceEventTime(200)
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, movedView)
        assertTrue(viewState.isUserMovingView)
        assertNotEquals(before, viewState.visibleRange)

        // a finger that holds the view still is still moving it
        composeRule.mainClock.advanceTimeBy(2000)
        assertTrue(viewState.isUserMovingView)
        val result = scroll { scrollToPosition(state.lineStart(900), animate = false) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(false, result())
        assertFalse(viewState.isPositionVisible(state.lineStart(900)))

        // held still for long enough, so this has no speed left for a fling
        composeRule.onNodeWithTag("editor").performTouchInput {
            advanceEventTime(2000)
            up()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.waitForIdle()
        assertFalse(viewState.isUserMovingView)
        assertEquals(1, movedView)
    }

    @Test
    fun `the movement lasts until the fling has ended`() {
        val state = KodeEditorState(longText)
        show(state)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("editor").performTouchInput { swipeUp(durationMillis = 100) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertTrue("the fling still moves the view", viewState.isUserMovingView)

        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(20000)
        composeRule.waitForIdle()
        assertFalse(viewState.isUserMovingView)
        assertEquals(1, movedView)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a zoom with the keyboard is reported without a lasting movement`() {
        show(KodeEditorState(longText))
        composeRule.onNodeWithTag("editor").performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("editor").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Equals)
            keyUp(Key.CtrlLeft)
        }
        composeRule.waitForIdle()

        assertEquals(1, movedView)
        assertFalse(viewState.isUserMovingView)
    }

    @Test
    fun `lines that wrap differently are not a movement of the user`() {
        val state = KodeEditorState("wrapped words ".repeat(2000))
        show(state, lineWrapping = true)
        scroll { scrollToPosition(state.rawText.length, animate = false) }
        composeRule.waitForIdle()

        composeRule.runOnIdle { state.setText("short") }
        composeRule.waitForIdle()

        assertEquals(0, movedView)
        assertFalse(viewState.isUserMovingView)
    }

    @Test
    fun `the visible range is the rows on the screen`() {
        val state = KodeEditorState(longText)
        show(state)
        val height = layout!!.lineHeight
        val visibleRows = composeRule.onNodeWithTag("editor").fetchSemanticsNode().size.height / height

        val range = viewState.visibleRange!!

        assertEquals(0, range.start)
        assertEquals(kotlin.math.ceil(visibleRows).toInt() - 1, state.lineOf(range.end))
        assertEquals(state.lineEnd(state.lineOf(range.end)), range.end)
    }

    @Test
    fun `the visible range follows the view and the text`() {
        val state = KodeEditorState(longText)
        show(state)
        val position = state.lineStart(500)

        scroll { scrollToPosition(position, animate = false) }
        composeRule.waitForIdle()
        val range = viewState.visibleRange!!
        assertTrue(position in range.start..range.end)

        composeRule.runOnIdle { state.setText("short") }
        composeRule.waitForIdle()
        assertEquals(TextRange(0, 5), viewState.visibleRange)
    }

    @Test
    fun `the visible range covers parts of wrapped lines`() {
        val state = KodeEditorState("wrapped words ".repeat(2000))
        show(state, lineWrapping = true)

        val range = viewState.visibleRange!!

        assertEquals(0, range.start)
        assertTrue("only a part of the line is visible", range.end < state.rawText.length)
    }

    @Test
    fun `a range ends up in the center`() {
        val state = KodeEditorState(longText)
        show(state)
        val start = state.lineStart(500)
        val end = state.lineEnd(510)

        val result = scroll { scrollToRange(start, end, animate = false) }
        composeRule.waitForIdle()

        assertEquals(true, result())
        val visible = viewState.visibleRange!!
        val above = 505 - state.lineOf(visible.start)
        val below = state.lineOf(visible.end) - 505
        assertTrue("$above rows above and $below below", kotlin.math.abs(above - below) <= 2)
    }

    @Test
    fun `a range that is larger than the editor has its middle in the center`() {
        val state = KodeEditorState(longText)
        show(state)

        scroll { scrollToRange(state.lineStart(100), state.lineEnd(900), animate = false) }
        composeRule.waitForIdle()

        assertTrue(viewState.isPositionVisible(state.lineStart(500)))
    }

    @Test
    fun `a range near the center does not move the view`() {
        val viewport = Size(100f, 1000f)
        val offset = Offset(7f, 500f)
        val max = Offset(0f, 5000f)

        fun at(middle: Float) = CursorVisibility.centeredOffsetFor(offset, middle - 10, middle + 10, viewport, max)

        assertEquals(offset, at(1000f))
        assertEquals(offset, at(1100f))
        assertEquals(offset, at(900f))
        assertEquals(Offset(7f, 601f), at(1101f))
        assertEquals("the top of the text is the limit", Offset(7f, 0f), at(100f))
        assertEquals("the end of the text is the limit", Offset(7f, 5000f), at(9000f))
    }

    /** paints the lines at the top of the text in one color and all others in another, to find them on the screen */
    private class TopLinesColumn(private val topLines: Int) : KodeEditorGutterColumn {
        override fun width(lineCount: Int) = 40
        override fun DrawScope.drawCell(cell: GutterCell) {
            drawRect(if (cell.line < topLines) Color.Red else Color.Green, size = Size(cell.width, cell.height))
        }
    }

    private fun SemanticsNodeInteraction.colorsOfTheBar(): Set<Color> {
        val image = captureToImage().toPixelMap()
        return (0 until image.height step 4).mapTo(HashSet()) { image[20, it] }
    }

    private fun assertOpensAtThePosition(lineWrapping: Boolean) {
        val state = KodeEditorState(longText)
        val target = state.lineStart(500)
        composeRule.mainClock.autoAdvance = false
        var result: Boolean? = null
        composeRule.setContent {
            LaunchedEffect(Unit) { result = viewState.scrollToPosition(target, animate = false) }
            KodeEditor(
                modifier = Modifier.size(400.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                gutterColumns = listOf(TopLinesColumn(topLines = 400)),
                lineWrapping = lineWrapping,
                viewState = viewState,
            )
        }
        val node = composeRule.onNodeWithTag("editor")
        assertFalse("the first frame draws the top of the text", Color.Red in node.colorsOfTheBar())
        repeat(8) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            assertFalse("frame $frame draws the top of the text", Color.Red in node.colorsOfTheBar())
        }
        assertEquals(true, result)
        assertTrue(viewState.isPositionVisible(target))
        assertTrue("the text is shown again", Color.Green in node.colorsOfTheBar())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an editor that opens at a position never draws the top of the text`() {
        assertOpensAtThePosition(lineWrapping = false)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `an editor with line wrapping opens at the position too`() {
        assertOpensAtThePosition(lineWrapping = true)
    }

    @Test
    fun `a finger that stops a fling holds the view until it leaves`() {
        val changes = mutableListOf<Boolean>()
        val movement = GestureMovement { changes += it }

        movement.onFingerDown()
        assertEquals("a touch alone is no movement", emptyList<Boolean>(), changes)
        movement.onGestureMoved()
        movement.onFlingStarted()
        movement.onFingersGone()
        assertEquals("the fling goes on", listOf(true), changes)

        movement.onFingerDown()
        movement.onFlingEnded()
        assertEquals("the finger holds the view", listOf(true), changes)
        movement.onFingersGone()
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun `a tap is no movement`() {
        val changes = mutableListOf<Boolean>()
        val movement = GestureMovement { changes += it }

        movement.onFingerDown()
        movement.onFingersGone()

        assertEquals(emptyList<Boolean>(), changes)
    }
}
