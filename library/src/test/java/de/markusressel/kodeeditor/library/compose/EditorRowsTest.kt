package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class EditorRowsTest {

    private fun rowsOf(state: KodeEditorState, cells: Int = 10): WrappedRows {
        val rows = WrappedRows(state) { text, start, end, width -> cellBreaks(text, start, end, width.toInt()) }
        rows.update(cells.toFloat())
        state.rowObserver = rows::onSplice
        return rows
    }

    private fun shown(text: String, rows: EditorRows) = (0 until rows.count).map { text.substring(rows.start(it), rows.end(it)) }

    @Test
    fun `a line that fits does not break`() {
        assertNull(cellBreaks("0123456789", 0, 10, 10))
        assertNull(cellBreaks("", 0, 0, 10))
    }

    @Test
    fun `a line breaks after the last space that fits`() {
        assertArrayEquals(intArrayOf(6), cellBreaks("hello world", 0, 11, 10))
    }

    @Test
    fun `a line breaks after a hyphen`() {
        assertArrayEquals(intArrayOf(7), cellBreaks("Zauber-Werkzeug", 0, 15, 10))
    }

    @Test
    fun `a word that does not fit into a row breaks anywhere`() {
        assertArrayEquals(intArrayOf(4, 8), cellBreaks("0123456789", 0, 10, 4))
    }

    @Test
    fun `a row of one character still gets through the line`() {
        assertArrayEquals(intArrayOf(1, 2, 3), cellBreaks("a b ", 0, 4, 1))
    }

    @Test
    fun `positions are relative to the start of the line`() {
        assertArrayEquals(intArrayOf(6), cellBreaks("xxx\nhello world", 4, 15, 10))
    }

    @Test
    fun `rows and lines map to each other`() {
        val text = "short\nhello big world\n\nend"
        val rows = rowsOf(KodeEditorState(text))

        assertEquals(listOf("short", "hello big ", "world", "", "end"), shown(text, rows))
        assertEquals(listOf(0, 1, 1, 2, 3), (0 until rows.count).map { rows.lineOf(it) })
        assertEquals(1, rows.firstRow(1))
        assertEquals(2, rows.lastRow(1))
        assertEquals(3, rows.firstRow(2))
    }

    @Test
    fun `a position at a break belongs to the next row, the end of the line to its last one`() {
        val text = "hello big world\nx"
        val rows = rowsOf(KodeEditorState(text))

        assertEquals(0, rows.rowOf(9))
        assertEquals(1, rows.rowOf(10))
        assertEquals(1, rows.rowOf(15))
        assertEquals(2, rows.rowOf(16))
    }

    @Test
    fun `rows follow typing, deleting and pasting the same way as rows made from scratch`() {
        repeat(50) { seed ->
            val random = Random(seed)
            val state = KodeEditorState("start of the text\n\nsecond line that is long enough to wrap\nend")
            val rows = rowsOf(state)
            repeat(200) {
                val text = state.rawText
                val start = random.nextInt(text.length + 1)
                val end = (start + random.nextInt(8)).coerceAtMost(text.length)
                val insert = listOf("", "a", " ", "\n", "word word word ", "x\ny\nz").random(random)
                state.localEdit(start, end, insert)

                val fresh = WrappedRows(state) { t, s, e, width -> cellBreaks(t, s, e, width.toInt()) }
                fresh.update(10f)
                assertEquals("seed $seed", shown(state.rawText, fresh), shown(state.rawText, rows))
            }
        }
    }

    @Test
    fun `remote changes reach the rows too`() {
        val state = KodeEditorState("one\ntwo")
        val rows = rowsOf(state)
        state.applyRemote(listOf(TextSplice(4, 0, "a long line of text\n")))

        assertEquals(listOf("one", "a long ", "line of ", "text", "two"), shown(state.rawText, rows))
    }

    @Test
    fun `a selection change leaves the rows alone`() {
        val state = KodeEditorState("hello big world")
        val rows = rowsOf(state)
        state.setSelection(TextRange(3))
        assertEquals(2, rows.count)
    }

    @Test
    fun `the same text stays in view when the width changes`() {
        val text = "aaaa bbbb cccc dddd eeee ffff\nsecond"
        val state = KodeEditorState(text)
        val rows = rowsOf(state)
        assertEquals(listOf("aaaa bbbb ", "cccc dddd ", "eeee ffff", "second"), shown(text, rows))

        rows.update(5f)
        assertEquals(listOf("aaaa ", "bbbb ", "cccc ", "dddd ", "eeee ", "ffff", "secon", "d"), shown(text, rows))

        // the middle of the row with "cccc" of the old width
        assertEquals(2.5f, rows.takePositionAfterResize(1.5f))
        assertNull(rows.takePositionAfterResize(1.5f))
    }

    @Test
    fun `a visible anchor stays in its place when the width changes`() {
        val text = "aaaa bbbb cccc dddd eeee ffff\nsecond"
        val state = KodeEditorState(text)
        val rows = rowsOf(state)

        rows.update(5f)

        // "eeee" was in row 2, which is 0.5 below the position, and is in row 4 now
        assertEquals(3.5f, rows.takePositionAfterResize(1.5f, listOf(text.indexOf("eeee")), 3f))
    }

    @Test
    fun `the first visible anchor is used`() {
        val text = "aaaa bbbb cccc dddd eeee ffff\nsecond"
        val state = KodeEditorState(text)
        val rows = rowsOf(state)

        rows.update(5f)

        // "second" is below the view, so "cccc" decides: it was in row 1 and is in row 2 now
        assertEquals(1f, rows.takePositionAfterResize(0f, listOf(text.indexOf("second"), text.indexOf("cccc")), 2f))
    }

    @Test
    fun `an anchor out of view does not move the view`() {
        val text = "aaaa bbbb cccc dddd eeee ffff\nsecond"
        val state = KodeEditorState(text)
        val rows = rowsOf(state)

        rows.update(5f)

        assertEquals(2.5f, rows.takePositionAfterResize(1.5f, listOf(0), 1f))
    }

    @Test
    fun `a position in a line that is gone stays within the text`() {
        val state = KodeEditorState("a\nb\nc\nd")
        val rows = rowsOf(state)
        rows.update(5f)
        state.localEdit(0, state.rawText.length, "x")
        assertEquals(0f, rows.takePositionAfterResize(3f))
    }
}
