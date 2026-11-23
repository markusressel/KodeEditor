package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.robolectric.annotation.GraphicsMode

/** shows an editor with the cursor of Jane, and tells where her label is and how often it was composed */
private class RemoteCursorScene(private val rule: ComposeContentTestRule) {
    private companion object {
        const val LABEL = "label"
    }

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    // a long first line, for moves farther than the rules let a cursor glide
    val state = KodeEditorState(
        "long ".repeat(60) + "\n" + (1 until 40).joinToString("\n") { "line number $it with some text" },
    )
    val cursor: MutableState<KodeEditorRemoteCursor> = mutableStateOf(janeAt(0))
    /** whether Jane is there, she leaves with false */
    val present = mutableStateOf(true)
    /** where the label is drawn now */
    val labelPosition: Offset
        get() = rule.onNodeWithTag(LABEL, useUnmergedTree = true).fetchSemanticsNode().positionInRoot
    var labelCompositions = 0
    /** whether the label of Jane is there, also while it fades out */
    val labelShown: Boolean
        get() = rule.onAllNodesWithTag(LABEL, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    var layout: KodeEditorLayout? = null

    fun janeAt(offset: Int, selection: TextRange = TextRange(offset)) =
        KodeEditorRemoteCursor("jane", listOf(selection), Color.Red, Color.Green.copy(alpha = 0.5f))

    fun show(animations: KodeEditorAnimations) {
        rule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onTextLayout = { layout = it },
                animations = animations,
                gutterColumns = emptyList(),
                remoteCursors = if (present.value) listOf(cursor.value) else emptyList(),
                remoteCursorLabel = {
                    SideEffect { labelCompositions++ }
                    Box(Modifier.size(10.dp).testTag(LABEL))
                },
            )
        }
        rule.waitForIdle()
    }

    /** moves the cursor of Jane, and lets one frame pass */
    fun moveTo(offset: Int, selection: TextRange = TextRange(offset)) {
        rule.runOnIdle { cursor.value = janeAt(offset, selection) }
        frame()
    }

    /**
     * lets one frame pass. The clock is paused, so nothing else tells the composition and the flows about changes of
     * the state, which the frame clock of an app does each frame
     */
    fun frame() {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
    }

    /** Jane leaves, and one frame passes */
    fun leave() {
        rule.runOnIdle { present.value = false }
        frame()
    }

    /** lets the frames pass until nothing moves anymore */
    fun settle() {
        repeat(5) { frame() }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
    }

    /** where the label is once nothing moves anymore */
    fun settledPosition(): Offset {
        settle()
        return labelPosition
    }
}

// real text measurement, the legacy one has no realistic widths of characters
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class RemoteCursorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scene = RemoteCursorScene(composeRule)

    private val glide = KodeEditorAnimations(appearance = KodeEditorAppearance())

    @Test
    fun `without an appearance the cursor jumps`() {
        scene.show(KodeEditorAnimations())
        val start = scene.labelPosition
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(8)
        val first = scene.labelPosition

        assertTrue("it moved", first.x > start.x)
        assertEquals("all the way at once", scene.settledPosition(), first)
    }

    @Test
    fun `the cursor glides to its new place and slows down`() {
        scene.show(glide)
        val start = scene.labelPosition
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(3)
        // the glide starts with the next frame
        scene.frame()
        scene.frame()
        val early = scene.labelPosition
        scene.frame()
        scene.frame()
        val later = scene.labelPosition
        val end = scene.settledPosition()

        assertTrue("on its way: $start $early $end", early.x > start.x && early.x < end.x)
        assertTrue("further: $early $later", later.x > early.x && later.x < end.x)
        assertTrue("slower towards the end", later.x - early.x < (early.x - start.x) * 2)
        assertEquals(start.y, end.y, 0.01f)
    }

    @Test
    fun `a glide does not compose the label again for each frame`() {
        scene.show(glide)
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(3)
        val compositions = scene.labelCompositions
        repeat(5) { scene.frame() }
        scene.settledPosition()

        assertEquals(compositions, scene.labelCompositions)
    }

    /** moves the cursor of Jane, and tells whether it is drawn at its new place right away */
    private fun jumps(offset: Int): Boolean {
        composeRule.mainClock.autoAdvance = false
        scene.moveTo(offset)
        scene.frame()
        scene.frame()
        val drawn = scene.labelPosition
        return drawn == scene.settledPosition()
    }

    @Test
    fun `a move to the next row glides`() {
        scene.show(glide)
        val start = scene.labelPosition
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(scene.state.lineStart(1) + 2)
        scene.frame()
        scene.frame()
        val early = scene.labelPosition
        val end = scene.settledPosition()

        // the label is below the cursor on the first row, and above it on the others
        assertTrue("on its way: $start $early $end", early.x > start.x && early.x < end.x && early != end)
    }

    @Test
    fun `a move two rows down jumps`() {
        scene.show(glide)

        assertTrue(jumps(scene.state.lineStart(2)))
    }

    @Test
    fun `a far jump jumps`() {
        scene.show(glide)

        assertTrue(jumps(scene.state.lineStart(10) + 5))
    }

    @Test
    fun `a move within the row farther than a part of the width jumps`() {
        scene.show(glide)

        assertTrue(jumps(250))
    }

    @Test
    fun `a move to the next row far sideways jumps`() {
        scene.show(glide)
        composeRule.runOnIdle { scene.cursor.value = scene.janeAt(100) }
        scene.settledPosition()

        assertTrue(jumps(scene.state.lineStart(1)))
    }

    @Test
    fun `a cursor that moves with the text does not glide`() {
        scene.show(glide)
        composeRule.runOnIdle { scene.cursor.value = scene.janeAt(10) }
        scene.settledPosition()
        composeRule.mainClock.autoAdvance = false

        // the text in front of it grows, the cursor moves with its character, which slides out of the way
        composeRule.runOnIdle {
            scene.state.applyRemote(listOf(TextSplice(0, 0, "abc")))
            scene.cursor.value = scene.janeAt(13)
        }
        scene.frame()
        scene.frame()
        val first = scene.labelPosition
        val layout = scene.layout as EditorLineLayout
        val sliding = layout.shownCursorRect(13).topLeft - layout.getCursorRect(13).topLeft
        val settled = scene.settledPosition()

        assertTrue("the text slides: $sliding", sliding.x < -1f)
        assertEquals(settled.x + sliding.x, first.x, 1f)
        assertEquals(settled.y, first.y, 1f)
    }

    @Test
    fun `a new target while gliding glides on from where the cursor is`() {
        scene.show(glide)
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(2)
        scene.frame()
        scene.frame()
        val between = scene.labelPosition
        scene.moveTo(4)
        val after = scene.labelPosition
        val end = scene.settledPosition()

        assertTrue("no jump back to the old place: $between $after $end", after.x >= between.x && after.x < end.x)
    }

    /** @return the left and right edge of the selection of Jane in the row of a [line], null without one */
    private fun selectionIn(line: Int = 0): ClosedFloatingPointRange<Float>? {
        val image = composeRule.onNodeWithTag("editor").captureToImage().toPixelMap()
        val layout = scene.layout!!
        val top = layout.getLineTop(line).toInt()
        val rows = top until (top + layout.lineHeight).toInt()
        val xs = (0 until image.width).filter { x ->
            rows.any { y -> image[x, y].let { c -> c.green > c.red + 0.2f && c.green > c.blue + 0.2f } }
        }
        return if (xs.isEmpty()) null else xs.first().toFloat()..xs.last().toFloat()
    }

    /** lets a glide start and move a little */
    private fun glideStarted() {
        scene.frame()
        scene.frame()
        scene.frame()
    }

    @Test
    fun `a selection grows from the cursor`() {
        scene.show(glide)
        composeRule.runOnIdle { scene.cursor.value = scene.janeAt(2) }
        scene.settledPosition()
        assertEquals(null, selectionIn())
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(6, TextRange(2, 6))
        glideStarted()
        val growing = selectionIn()!!
        scene.settledPosition()
        val selected = selectionIn()!!

        assertEquals("it starts at the cursor", selected.start, growing.start, 2f)
        assertTrue("on its way: $growing $selected", growing.endInclusive < selected.endInclusive - 2)
    }

    @Test
    fun `a selection shrinks into the cursor`() {
        scene.cursor.value = scene.janeAt(6, TextRange(2, 6))
        scene.show(glide)
        val selected = selectionIn()!!
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(6)
        glideStarted()
        val shrinking = selectionIn()!!
        scene.settledPosition()

        assertTrue("on its way: $shrinking $selected", shrinking.start > selected.start + 2)
        assertEquals(selected.endInclusive, shrinking.endInclusive, 2f)
        assertEquals("gone", null, selectionIn())
    }

    @Test
    fun `a long selection shrinks into the cursor too`() {
        // the start moves farther than a cursor may, only the cursor counts
        scene.cursor.value = scene.janeAt(100, TextRange(0, 100))
        scene.show(glide)
        val selected = selectionIn()!!
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(100)
        glideStarted()
        val shrinking = selectionIn()!!
        scene.settledPosition()

        assertTrue("on its way: $shrinking $selected", shrinking.start > selected.start + 2)
        assertEquals(selected.endInclusive, shrinking.endInclusive, 2f)
        assertEquals("gone", null, selectionIn())
    }

    @Test
    fun `a user who leaves fades out`() {
        scene.cursor.value = scene.janeAt(6, TextRange(2, 6))
        scene.show(glide)
        val selected = selectionIn()!!
        composeRule.mainClock.autoAdvance = false

        scene.leave()
        glideStarted()
        val fading = selectionIn()
        val labelWhileFading = scene.labelShown
        scene.settle()

        assertEquals("the selection fades where it was", selected, fading)
        assertTrue("the label fades too", labelWhileFading)
        assertEquals("gone", null, selectionIn())
        assertFalse("the label is gone", scene.labelShown)
    }

    @Test
    fun `a user who comes back while fading out stays`() {
        scene.cursor.value = scene.janeAt(6, TextRange(2, 6))
        scene.show(glide)
        val selected = selectionIn()!!
        composeRule.mainClock.autoAdvance = false

        scene.leave()
        scene.frame()
        composeRule.runOnIdle { scene.present.value = true }
        scene.settle()

        assertEquals(selected, selectionIn())
        assertTrue(scene.labelShown)
    }

    @Test
    fun `without an appearance a user who leaves is gone at once`() {
        scene.cursor.value = scene.janeAt(6, TextRange(2, 6))
        scene.show(KodeEditorAnimations())
        composeRule.mainClock.autoAdvance = false

        scene.leave()
        scene.frame()

        assertEquals(null, selectionIn())
        assertFalse(scene.labelShown)
    }

    @Test
    fun `both ends of a selection glide to the new one`() {
        scene.cursor.value = scene.janeAt(4, TextRange(0, 4))
        scene.show(glide)
        val before = selectionIn()!!
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(7, TextRange(3, 7))
        glideStarted()
        val moving = selectionIn()!!
        scene.settledPosition()
        val after = selectionIn()!!

        val values = "$before $moving $after"
        assertTrue("start on its way: $values", moving.start > before.start && moving.start < after.start)
        assertTrue("end on its way: $values", moving.endInclusive > before.endInclusive)
        assertTrue("end on its way: $values", moving.endInclusive < after.endInclusive)
    }

    @Test
    fun `a selection glides without composing the label again`() {
        scene.show(glide)
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(4, TextRange(0, 4))
        val compositions = scene.labelCompositions
        repeat(5) { scene.frame() }
        scene.settledPosition()

        assertEquals(compositions, scene.labelCompositions)
    }

    @Test
    fun `a selection far away appears at once`() {
        scene.show(glide)
        composeRule.mainClock.autoAdvance = false
        val start = scene.state.lineStart(10)

        scene.moveTo(start + 4, TextRange(start, start + 4))
        glideStarted()
        val first = selectionIn(10)

        assertEquals(scene.run { settledPosition(); selectionIn(10) }, first)
    }

    @Test
    fun `the caret and the selection are drawn`() {
        scene.cursor.value = scene.janeAt(4, TextRange(0, 4))
        scene.show(KodeEditorAnimations.None)

        val image = composeRule.onNodeWithTag("editor").captureToImage().toPixelMap()
        val layout = scene.layout!!
        val y = (layout.getLineTop(0) + layout.lineHeight / 2).toInt()
        val row = (0 until image.width).map { image[it, y] }.toSet()
        assertTrue("the caret: $row", Color.Red in row)
        assertTrue("the selection", row.any { it.green > 0.3f && it.red < 0.3f && it.blue < 0.3f })
    }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class RemoteCursorsWithoutAnimationsTest {

    @get:Rule
    val composeRule = createComposeRule(
        effectContext = object : MotionDurationScale {
            override val scaleFactor = 0f
        },
    )

    private val scene = RemoteCursorScene(composeRule)

    @Test
    fun `the cursor jumps when the system turns animations off`() {
        scene.show(KodeEditorAnimations(appearance = KodeEditorAppearance()))
        composeRule.mainClock.autoAdvance = false

        scene.moveTo(20)
        val first = scene.labelPosition

        assertEquals(scene.settledPosition(), first)
    }

    @Test
    fun `a user who leaves is gone at once when the system turns animations off`() {
        scene.show(KodeEditorAnimations(appearance = KodeEditorAppearance()))
        composeRule.mainClock.autoAdvance = false

        scene.leave()
        scene.frame()

        assertFalse(scene.labelShown)
    }
}
