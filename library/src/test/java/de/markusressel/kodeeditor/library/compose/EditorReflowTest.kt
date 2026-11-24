package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import de.markusressel.kodeeditor.library.compose.animation.LayoutBeforeInsert
import de.markusressel.kodeeditor.library.compose.animation.REFLOW_ANIMATION_MS
import de.markusressel.kodeeditor.library.compose.animation.ReflowTransition
import de.markusressel.kodeeditor.library.compose.layout.WrappedRows
import de.markusressel.kodeeditor.library.compose.layout.cellBreaks
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.TextSplice
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

    private fun beforeInsert(text: String, start: Int, length: Int, firstRows: IntArray, breaks: Array<IntArray?>) =
        LayoutBeforeInsert(
            text = text,
            lineStarts = KodeEditorState(text).lineStarts(),
            start = start,
            length = length,
            firstRows = firstRows,
            breaks = breaks,
            lineHeight = 20f,
            measure = { _, from, to -> (to - from) * 10f },
        )

    private fun beforeChange(text: String, start: Int, length: Int, removed: String, firstRows: IntArray) =
        LayoutBeforeInsert(
            text = text,
            lineStarts = KodeEditorState(text).lineStarts(),
            start = start,
            length = length,
            firstRows = firstRows,
            breaks = arrayOfNulls(firstRows.size - 1),
            lineHeight = 20f,
            measure = { _, from, to -> (to - from) * 10f },
            removed = removed,
        )

    @Test
    fun `text behind a removal starts behind the removed text`() {
        // "hello brave world" became "hello world"
        val before = beforeChange("hello world", 6, 0, "brave ", intArrayOf(0, 1))
        assertEquals(Offset(30f, 0f), before.positionOf(3))
        assertEquals("the cursor where the removal was", Offset(120f, 0f), before.positionOf(6))
        assertEquals(Offset(130f, 0f), before.positionOf(7))
    }

    @Test
    fun `text behind a removed line break starts in the next row`() {
        // "ab\ncd" became "abcd"
        val before = beforeChange("abcd", 2, 0, "\n", intArrayOf(0, 1, 2))
        assertEquals(Offset(10f, 0f), before.positionOf(1))
        assertEquals(Offset(0f, 20f), before.positionOf(2))
        assertEquals(Offset(10f, 20f), before.positionOf(3))
        assertEquals("the joined line broke where the line ended", listOf(2), before.breaksOf(0)?.toList())
    }

    @Test
    fun `text behind a replacement starts behind the replaced text`() {
        // "hello wrold" became "hello world": "wrold" replaced by "world"
        val before = beforeChange("hello world!", 6, 5, "wrold", intArrayOf(0, 1))
        assertEquals("the new text starts where the old one did", Offset(60f, 0f), before.positionOf(8))
        assertEquals(Offset(110f, 0f), before.positionOf(11))
    }

    @Test
    fun `text behind an insertion starts where the insertion is`() {
        // "hello world" became "hello brave world"
        val before = beforeInsert("hello brave world", 6, 6, intArrayOf(0, 1), arrayOf(null))
        assertEquals(Offset(30f, 0f), before.positionOf(3))
        assertEquals("the inserted text", Offset(60f, 0f), before.positionOf(8))
        assertEquals(Offset(60f, 0f), before.positionOf(12))
        assertEquals(Offset(100f, 0f), before.positionOf(16))
    }

    @Test
    fun `text behind an insertion starts in the row it was in`() {
        // "hello world" broke behind "hello ", the break is behind the inserted text now
        val before = beforeInsert("hello brave world", 6, 6, intArrayOf(0, 2), arrayOf(intArrayOf(6)))
        assertEquals(Offset(0f, 20f), before.positionOf(12))
        assertEquals(Offset(20f, 20f), before.positionOf(14))
        assertEquals(listOf(12), before.breaksOf(0)?.toList())
    }

    @Test
    fun `lines behind inserted lines start in their rows from before`() {
        // "ab\ncd" became "aX\nYb\ncd"
        val before = beforeInsert("aX\nYb\ncd", 1, 3, intArrayOf(0, 1, 2), arrayOf(null, null))
        assertEquals(Offset(10f, 0f), before.positionOf(4))
        assertEquals(Offset(0f, 20f), before.positionOf(6))
        assertEquals(Offset(10f, 20f), before.positionOf(7))
        assertEquals(0, before.toBefore(0))
        assertEquals(1, before.toBefore(2))
        assertEquals(3, before.toBefore(6))
    }

    @Test
    fun `an insertion during another one starts where the text is`() {
        val text = KodeEditorState("ab")
        val first = ReflowTransition(
            state = text,
            firstRows = IntArray(0),
            breaks = emptyArray(),
            previous = null,
            lineHeight = 20f,
            measure = { _, _ -> 0f },
            durationMs = 100,
            inserted = beforeInsert("aXb", 1, 1, intArrayOf(0, 1), arrayOf(null)),
        )
        first.onFrame(1000)
        first.onFrame(1050)
        text.applyRemote(listOf(TextSplice(2, 0, "Y")))
        val second = ReflowTransition(
            state = text,
            firstRows = IntArray(0),
            breaks = emptyArray(),
            previous = first,
            lineHeight = 20f,
            measure = { _, _ -> 0f },
            durationMs = 100,
            inserted = beforeInsert("aXYb", 2, 1, intArrayOf(0, 1), arrayOf(null)),
        )
        // "b" was at 10 before the first insertion, at 20 before the second one, and is on its way
        val start = second.startOf(3).x
        assertTrue("$start", start > 10f && start < 20f)
    }
}
