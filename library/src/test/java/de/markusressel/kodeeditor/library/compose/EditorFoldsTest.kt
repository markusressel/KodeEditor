package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFoldsTest {

    private fun foldsOf(text: String): Pair<KodeEditorState, EditorFolds> {
        val state = KodeEditorState(text)
        val folds = EditorFolds(state, MarkdownFoldProvider)
        state.foldObserver = folds.spliceObserver
        state.addListener(folds)
        return state to folds
    }

    private val document = "# a\n1\n2\n\n# b\n3\n\n# c\n4"

    @Test
    fun hidesTheLinesOfARegion() {
        val (_, folds) = foldsOf(document)
        assertTrue(folds.toggle(0))
        assertTrue(folds.isFolded(0))
        assertFalse(folds.isHidden(0))
        assertTrue(folds.isHidden(1))
        assertTrue(folds.isHidden(2))
        assertFalse(folds.isHidden(3))
        assertTrue(folds.toggle(0))
        assertFalse(folds.isHidden(1))
    }

    @Test
    fun aLineWithoutARegionDoesNotFold() {
        val (_, folds) = foldsOf(document)
        assertFalse(folds.toggle(1))
        assertEquals(null, folds.hidden)
    }

    @Test
    fun foldingMovesAHiddenCursorToTheEndOfTheLine() {
        val (state, folds) = foldsOf(document)
        state.setSelection(TextRange(5))
        folds.toggle(0)
        assertEquals(TextRange(3), state.selection)
        assertTrue(folds.isFolded(0))
    }

    @Test
    fun aCursorInAFoldUnfoldsIt() {
        val (state, folds) = foldsOf(document)
        folds.toggle(0)
        state.setSelection(TextRange(5))
        assertFalse(folds.isFolded(0))
    }

    @Test
    fun foldsMoveWithTheText() {
        val (state, folds) = foldsOf(document)
        folds.toggle(4)
        state.setSelection(TextRange(0))
        state.replaceSelection("new\n")
        assertTrue(folds.isFolded(5))
        assertTrue(folds.isHidden(6))
        assertFalse(folds.isHidden(5))
    }

    @Test
    fun aLineThatIsNoHeadingAnymoreUnfolds() {
        val (state, folds) = foldsOf(document)
        folds.toggle(0)
        state.localEdit(0, 1, "")
        assertEquals(null, folds.hidden)
    }

    @Test
    fun aChangeAcrossTheEdgeUnfolds() {
        val (state, folds) = foldsOf(document)
        folds.toggle(0)
        // backspace at the start of the line below the fold
        state.localEdit(7, 8, "")
        assertFalse(folds.isFolded(0))
    }

    @Test
    fun aChangeWithinAFoldKeepsIt() {
        val (state, folds) = foldsOf(document)
        folds.toggle(0)
        state.applyRemote(listOf(TextSplice(4, 0, "x")))
        assertTrue(folds.isFolded(0))
        assertEquals(TextRange(0), state.selection)
    }

    @Test
    fun nestedFoldsStayWhenTheOuterOneOpens() {
        val (_, folds) = foldsOf("# a\n## b\n1\n## c\n2")
        folds.toggle(1)
        folds.toggle(0)
        assertTrue(folds.isHidden(1))
        folds.toggle(0)
        assertFalse(folds.isHidden(1))
        assertTrue(folds.isHidden(2))
    }

    @Test
    fun theCursorSkipsFoldedText() {
        val (_, folds) = foldsOf(document)
        folds.toggle(0)
        // "1" is at 4, "2" ends at 7, and the blank line is at 8
        assertEquals(8, folds.shownPosition(4, forward = true))
        assertEquals(3, folds.shownPosition(7, forward = false))
        assertEquals(8, folds.shownPosition(8, forward = true))
    }

    @Test
    fun theCursorStopsInFrontOfAFoldAtTheEnd() {
        val (state, folds) = foldsOf("# a\n1")
        folds.toggle(0)
        assertEquals(3, folds.shownPosition(state.rawText.length, forward = true))
    }

    @Test
    fun rowsLeaveOutHiddenLines() {
        val (state, folds) = foldsOf(document)
        folds.toggle(0)
        val rows = FoldedRows(UnwrappedRows(state), folds.hidden!!)
        assertEquals(state.lineCount - 2, rows.count)
        assertEquals(0, rows.lineOf(0))
        assertEquals(3, rows.lineOf(1))
        assertEquals(1, rows.firstRow(3))
        // a hidden line is at the end of the line that hides it
        assertEquals(0, rows.firstRow(1))
        assertEquals(0, rows.rowOf(5))
        assertEquals(state.lineStart(3), rows.start(1))
        assertEquals(state.lineEnd(3), rows.end(1))
    }

    @Test
    fun rowsWithWrappedLinesLeaveOutAllRowsOfHiddenLines() {
        val state = KodeEditorState("# a\n12345678\n\n# b\n1234")
        val folds = EditorFolds(state, MarkdownFoldProvider)
        val wrapped = WrappedRows(state) { text, start, end, _ -> cellBreaks(text, start, end, 4) }
        wrapped.update(100f)
        folds.toggle(0)
        val rows = FoldedRows(wrapped, folds.hidden!!)
        assertEquals(wrapped.count - 2, rows.count)
        assertEquals(2, rows.lineOf(1))
        assertEquals(2, rows.firstRow(3))
    }

    @Test
    fun shownFirstRowsLeaveOutHiddenLines() {
        val hidden = HiddenLines(intArrayOf(1), intArrayOf(2))
        // line 1 has two rows
        assertArrayEquals(intArrayOf(0, 1, 1, 1, 2), hidden.shownFirstRows(intArrayOf(0, 1, 3, 4, 5)))
    }
}
