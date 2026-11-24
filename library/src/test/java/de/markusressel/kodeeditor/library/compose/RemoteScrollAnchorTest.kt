package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAnimations
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAppearance
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Text that others insert or remove in front of what the user looks at does not move it on the screen */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class RemoteScrollAnchorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private lateinit var state: KodeEditorState
    private lateinit var layout: EditorLineLayout
    private val viewState = KodeEditorViewState()

    private val lines = (0 until 80).joinToString("\n") { "line $it of the text" }

    private fun show(text: String = lines, wrapping: Boolean = true, keepView: Boolean = true) {
        state = KodeEditorState(text)
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(300.dp, 200.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onTextLayout = { layout = it as EditorLineLayout },
                animations = KodeEditorAnimations(appearance = KodeEditorAppearance()),
                gutterColumns = emptyList(),
                lineWrapping = wrapping,
                keepViewOnRemoteChanges = keepView,
                viewState = viewState,
            )
        }
        composeRule.waitForIdle()
    }

    private val offset get() = composeRule.runOnIdle { viewState.viewportOffset() }

    private fun KodeEditorViewState.viewportOffset(): Offset = viewport!!.offset

    /** scrolls so the [line] is at the top, and puts the focused cursor at [column] of [caretLine] */
    private fun lookAt(line: Int, caretLine: Int?, column: Int = 3, x: Float = 0f) {
        if (caretLine != null) composeRule.onNodeWithTag("editor").performClick()
        composeRule.runOnIdle {
            if (caretLine != null) state.setSelection(TextRange(state.lineStart(caretLine) + column))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { viewState.viewport!!.moveTo(Offset(x, layout.getLineTop(line))) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    private fun frame() {
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** where the cursor of the user is drawn on the screen */
    private fun caretOnScreen(): Offset = composeRule.runOnIdle {
        layout.shownCursorRect(state.selection.end).topLeft - viewState.viewportOffset()
    }

    /** applies [change] and returns where the cursor is drawn on the screen in each frame after it */
    private fun caretWhile(change: () -> Unit): List<Offset> {
        composeRule.runOnIdle(change)
        return List(15) {
            frame()
            caretOnScreen()
        }
    }

    private fun assertStays(before: Offset, frames: List<Offset>) {
        assertTrue(
            "stays at $before: $frames",
            frames.all { abs(it.x - before.x) <= 1f && abs(it.y - before.y) <= 1f },
        )
    }

    @Test
    fun `text inserted far above the view keeps the cursor where it is on the screen`() {
        show()
        lookAt(line = 40, caretLine = 44)
        val before = caretOnScreen()

        val frames = caretWhile {
            state.applyRemote(listOf(TextSplice(state.lineStart(5), 0, "new\nlines\nabove\n")), appear = true)
        }

        assertStays(before, frames)
    }

    @Test
    fun `text inserted in the view above the cursor keeps the cursor where it is while it slides`() {
        show()
        lookAt(line = 40, caretLine = 44)
        val before = caretOnScreen()

        val frames = caretWhile {
            // a long piece that wraps into more rows, in a line above the cursor
            val at = state.lineStart(42) + 4
            state.applyRemote(listOf(TextSplice(at, 0, " a chunk of an agent that is long enough to wrap")), appear = true)
        }

        assertStays(before, frames)
    }

    @Test
    fun `text removed above keeps the cursor where it is`() {
        show()
        lookAt(line = 40, caretLine = 44)
        val before = caretOnScreen()

        val frames = caretWhile {
            state.applyRemote(listOf(TextSplice(state.lineStart(10), state.lineStart(15) - state.lineStart(10), "")))
        }

        assertStays(before, frames)
    }

    @Test
    fun `without the cursor in view, the first visible line stays where it is`() {
        show()
        lookAt(line = 40, caretLine = null)
        val top = composeRule.runOnIdle { layout.getLineTop(40) - offset.y }

        composeRule.runOnIdle { state.applyRemote(listOf(TextSplice(state.lineStart(5), 0, "new\nlines\n"))) }
        repeat(15) { frame() }

        composeRule.runOnIdle { assertEquals(top, layout.getLineTop(42) - offset.y, 1f) }
    }

    @Test
    fun `text inserted behind the cursor does not move the view`() {
        show()
        lookAt(line = 40, caretLine = 44)
        val before = offset

        composeRule.runOnIdle { state.applyRemote(listOf(TextSplice(state.lineStart(46), 0, "new\nlines\n"))) }
        repeat(15) { frame() }

        assertEquals(before, offset)
    }

    @Test
    fun `the view does not go beyond the end of the text`() {
        show()
        composeRule.runOnIdle { viewState.viewport!!.moveTo(Offset(0f, Float.MAX_VALUE)) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle {
            state.applyRemote(listOf(TextSplice(state.lineStart(10), state.lineStart(30) - state.lineStart(10), "")))
        }
        repeat(15) { frame() }

        composeRule.runOnIdle {
            val viewport = viewState.viewport!!
            assertEquals(viewport.maxOffset.y, viewport.offset.y, 1f)
        }
    }

    @Test
    fun `without wrapping, text inserted in the row of the cursor in front of it keeps it where it is`() {
        val long = (0 until 40).joinToString("\n") { "line $it " + "word ".repeat(40) }
        show(long, wrapping = false)
        lookAt(line = 10, caretLine = 14, column = 80, x = 400f)
        val before = caretOnScreen()

        val frames = caretWhile {
            state.applyRemote(listOf(TextSplice(state.lineStart(14) + 5, 0, "\twide ＷＩＤＥ inserted ")), appear = true)
        }

        assertStays(before, frames)
    }

    @Test
    fun `without wrapping, text inserted in another row does not move the view sideways`() {
        val long = (0 until 40).joinToString("\n") { "line $it " + "word ".repeat(40) }
        show(long, wrapping = false)
        lookAt(line = 10, caretLine = 14, column = 80, x = 400f)
        val before = offset

        composeRule.runOnIdle { state.applyRemote(listOf(TextSplice(state.lineStart(12) + 5, 0, "inserted "))) }
        repeat(15) { frame() }

        assertEquals(before, offset)
    }

    @Test
    fun `apps can turn it off`() {
        show(keepView = false)
        lookAt(line = 40, caretLine = 44)
        val before = offset

        composeRule.runOnIdle { state.applyRemote(listOf(TextSplice(state.lineStart(5), 0, "new\nlines\n"))) }
        repeat(15) { frame() }

        assertEquals(before, offset)
    }
}
