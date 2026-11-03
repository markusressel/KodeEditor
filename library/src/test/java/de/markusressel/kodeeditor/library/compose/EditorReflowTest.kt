package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorReflowTest {
    private val state = KodeEditorState("hello world again")

    private fun transition(
        breaks: IntArray?,
        previous: ReflowTransition? = null,
    ) = ReflowTransition(
        state = state,
        firstRows = intArrayOf(0, 1 + (breaks?.size ?: 0)),
        breaks = arrayOf(breaks),
        previous = previous,
        lineHeight = 20f,
        measure = { start, end -> (end - start) * 10f },
    )

    @Test
    fun `text starts where it was before the change`() {
        val reflow = transition(intArrayOf(6, 12))
        assertEquals(Offset(0f, 0f), reflow.startOf(0))
        assertEquals(Offset(20f, 20f), reflow.startOf(8))
        assertEquals(Offset(0f, 40f), reflow.startOf(12))
    }

    @Test
    fun `scrolling for the reflow keeps the text in place`() {
        val reflow = transition(null)
        reflow.scrolled = 60f
        assertEquals(Offset(80f, 60f), reflow.startOf(8))
    }

    @Test
    fun `a reflow during another one starts where the text is`() {
        val first = transition(null)
        first.onFrame(1000)
        first.onFrame(1000L + REFLOW_ANIMATION_MS)
        val second = transition(intArrayOf(6), previous = first)
        assertEquals("the first one is over", Offset(20f, 20f), second.startOf(8))

        val running = transition(null)
        running.onFrame(1000)
        running.onFrame(1000L + REFLOW_ANIMATION_MS / 2)
        val third = transition(intArrayOf(6), previous = running)
        val start = third.startOf(8)
        assertTrue(start.x > 20f && start.x < 80f)
        assertTrue(start.y > 0f && start.y < 20f)
    }

    @Test
    fun `a change of the text ends the reflow`() {
        val reflow = transition(null)
        assertTrue(reflow.valid)
        state.applyRemote(listOf(TextSplice(0, 0, "x")))
        assertEquals(false, reflow.valid)
    }

    @Test
    fun `widths that are known to break alike do not go through the lines`() {
        var calls = 0
        val rows = WrappedRows(state, sameBreaks = { first, second -> first.toInt() == second.toInt() }) { text, start, end, width ->
            calls++
            cellBreaks(text, start, end, width.toInt())
        }
        rows.update(10f)
        val generation = rows.generation
        calls = 0
        rows.update(10.5f)
        assertEquals(0, calls)
        assertEquals(generation, rows.generation)
        rows.update(4f)
        assertEquals(1, calls)
    }

    @Test
    fun `only a width that breaks lines differently is a reflow`() {
        var reflows = 0
        val rows = WrappedRows(state, onReflow = { _, _ -> reflows++ }) { text, start, end, width ->
            cellBreaks(text, start, end, width.toInt())
        }
        rows.update(10f)
        assertEquals("the first width is no change", 0, reflows)
        rows.update(10.5f)
        assertEquals(0, reflows)
        rows.update(4f)
        assertEquals(1, reflows)
    }
}
