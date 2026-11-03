package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class EditorGutterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private class TestColumn(private val width: Int, private val takesTaps: Boolean = true) : KodeEditorGutterColumn {
        val tapped = mutableListOf<Int>()
        override fun width(lineCount: Int) = width
        override fun DrawScope.drawCell(cell: GutterCell) = Unit

        override fun onTap(line: Int): Boolean {
            tapped += line
            return takesTaps
        }
    }

    private var layout: KodeEditorLayout? = null

    private fun show(state: KodeEditorState, vararg columns: KodeEditorGutterColumn) {
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp).testTag("editor"),
                state = remember { state },
                languageRuleBook = noRules,
                colorScheme = noColors,
                onTextLayout = { layout = it },
                gutterColumns = columns.toList(),
            )
        }
        composeRule.waitForIdle()
    }

    private fun tap(x: Float, line: Int) {
        // the test clock starts at 0, which would make a first tap near the corner look like a double tap
        composeRule.mainClock.advanceTimeBy(1000)
        val y = layout!!.getLineTop(line) + layout!!.lineHeight / 2
        composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(x, y)); up() }
        composeRule.waitForIdle()
    }

    @Test
    fun `a position in the bar belongs to the column there`() {
        val first = TestColumn(10)
        val second = TestColumn(20)
        val columns = listOf(first, second)

        assertSame(first, gutterColumnAt(columns, 1, 0f))
        assertSame(first, gutterColumnAt(columns, 1, 9.9f))
        assertSame(second, gutterColumnAt(columns, 1, 10f))
        assertNull(gutterColumnAt(columns, 1, 30f))
    }

    @Test
    fun `the bar animates to a new width`() {
        val column = object : KodeEditorGutterColumn {
            val tapped = mutableListOf<Int>()
            override fun width(lineCount: Int) = if (lineCount < 10) 40 else 120
            override fun DrawScope.drawCell(cell: GutterCell) = Unit
            override fun onTap(line: Int): Boolean {
                tapped += line
                return true
            }
        }
        val state = KodeEditorState("\n".repeat(8))
        show(state, column)
        composeRule.mainClock.advanceTimeBy(1000)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { state.applyRemote(listOf(TextSplice(0, 0, "\n"))) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val y = layout!!.getLineTop(1) + layout!!.lineHeight / 2
        composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(100f, y)); up() }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("the bar does not reach this position yet", emptyList<Int>(), column.tapped)

        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(100f, y)); up() }
        composeRule.waitForIdle()
        assertEquals(listOf(1), column.tapped)
    }

    @Test
    fun `new line numbers fade in`() {
        val fades = LineNumberFades(9)
        fades.onLineCountChanged(10)
        assertEquals(0f, fades.alpha(9), 0f)
        assertEquals(1f, fades.alpha(8), 0f)
        assertEquals(emptyList<Int>(), fades.removedLines)

        fades.onFrame(1000)
        fades.onFrame(1000L + GUTTER_ANIMATION_MS / 2)
        assertEquals(0.5f, fades.alpha(9), 0.01f)

        fades.onFrame(1000L + GUTTER_ANIMATION_MS)
        assertEquals(1f, fades.alpha(9), 0f)
        assertFalse(fades.active)
    }

    @Test
    fun `removed line numbers fade out behind the end of the document`() {
        val fades = LineNumberFades(10)
        fades.onLineCountChanged(8)
        assertEquals(listOf(8, 9), fades.removedLines)
        assertEquals(1f, fades.alpha(9), 0f)

        fades.onFrame(1000)
        fades.onFrame(1000L + GUTTER_ANIMATION_MS)
        assertEquals(emptyList<Int>(), fades.removedLines)
        assertFalse(fades.active)
    }

    @Test
    fun `a line number that changes direction continues from where it is`() {
        val fades = LineNumberFades(9)
        fades.onLineCountChanged(10)
        fades.onFrame(1000)
        fades.onFrame(1000L + GUTTER_ANIMATION_MS / 2)

        fades.onLineCountChanged(9)
        assertEquals(0.5f, fades.alpha(9), 0.01f)
        assertEquals(listOf(9), fades.removedLines)

        fades.onFrame(2000)
        fades.onFrame(2000L + GUTTER_ANIMATION_MS)
        assertFalse(fades.active)
    }

    @Test
    fun `a large change does not fade`() {
        val fades = LineNumberFades(1)
        fades.onLineCountChanged(1000)
        assertFalse(fades.active)
        fades.onLineCountChanged(1)
        assertFalse(fades.active)
        assertEquals(emptyList<Int>(), fades.removedLines)
    }

    @Test
    fun `line numbers are as wide as the highest number needs`() {
        lateinit var column: KodeEditorGutterColumn
        composeRule.setContent { column = rememberLineNumberColumn() }
        composeRule.runOnIdle {
            assertTrue(column.width(9) < column.width(10))
            assertTrue(column.width(99) < column.width(100))
        }
    }

    @Test
    fun `a tap reaches the column and the line under it`() {
        val first = TestColumn(40)
        val second = TestColumn(40)
        val state = KodeEditorState("one\ntwo\nthree")
        show(state, first, second)

        tap(10f, 1)
        tap(50f, 2)

        assertEquals(listOf(1), first.tapped)
        assertEquals(listOf(2), second.tapped)
        assertEquals("a used tap leaves the cursor alone", TextRange(0), state.selection)
    }

    @Test
    fun `quick taps on a column are all taps and select nothing`() {
        val column = TestColumn(40)
        val state = KodeEditorState("one two")
        show(state, column)

        composeRule.mainClock.advanceTimeBy(1000)
        repeat(2) {
            composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(10f, 5f)); up() }
        }
        composeRule.waitForIdle()

        assertEquals(listOf(0, 0), column.tapped)
        assertEquals(TextRange(0), state.selection)
    }

    @Test
    fun `a column that does not use a tap lets it place the cursor`() {
        val column = TestColumn(40, takesTaps = false)
        val state = KodeEditorState("one\ntwo")
        state.setSelection(TextRange(2))
        show(state, column)

        tap(10f, 1)

        assertEquals(listOf(1), column.tapped)
        assertEquals(TextRange(4), state.selection)
    }

    @Test
    fun `a tap on the text does not reach a column`() {
        val column = TestColumn(40)
        show(KodeEditorState("one"), column)

        tap(200f, 0)

        assertEquals(emptyList<Int>(), column.tapped)
    }

    @Test
    fun `an editor works without a bar`() {
        val state = KodeEditorState("one\ntwo")
        show(state)

        tap(1f, 1)

        assertEquals(TextRange(4), state.selection)
    }

    @Test
    fun `the lines of the bar are those with a first row in the window`() {
        val state = KodeEditorState("one\ntwo\nthree")
        show(state, TestColumn(10))
        val layout = layout!!
        val height = layout.lineHeight

        assertEquals(
            listOf(
                GutterLine(1, height, layout.getLineBaseline(1)),
                GutterLine(2, 2 * height, layout.getLineBaseline(2)),
            ),
            gutterLinesWithin(layout, height * 1.5f, height * 2.5f),
        )
    }
}
