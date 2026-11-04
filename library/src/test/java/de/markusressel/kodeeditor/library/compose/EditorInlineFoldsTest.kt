package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EditorInlineFoldsTest {

    private val text = "ab [x](0123456789abcdef) cd\nnext line"
    private val start = text.indexOf('0')
    private val end = text.indexOf(')')

    private fun foldsOf(text: String, cursor: Int = text.length): Pair<KodeEditorState, EditorFolds> {
        val state = KodeEditorState(text)
        state.setSelection(TextRange(cursor))
        val folds = EditorFolds(state, MarkdownFoldProvider)
        state.foldObserver = folds.spliceObserver
        state.addListener(folds)
        return state to folds
    }

    private fun ranges(vararg starts: Int, ends: IntArray, placeholders: Array<String>) =
        CollapsedRanges(starts, ends, placeholders)

    @Test
    fun aCursorAwayFromAFoldCollapsesIt() {
        val (_, folds) = foldsOf(text)
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
    }

    @Test
    fun aCursorNextToAFoldLeavesItClosed() {
        val (state, folds) = foldsOf(text)
        state.setSelection(TextRange(start))
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
        state.setSelection(TextRange(end))
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
    }

    @Test
    fun aCursorWithinAFoldOpensItUntilItLeaves() {
        val (state, folds) = foldsOf(text)
        state.setSelection(TextRange(start + 3))
        assertNull(folds.inline.collapsed)
        // the edges are still at it
        state.setSelection(TextRange(end))
        assertNull(folds.inline.collapsed)
        state.setSelection(TextRange(end + 1))
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
    }

    @Test
    fun aTapOpensAFoldUntilTheCursorLeaves() {
        val (state, folds) = foldsOf(text)
        state.setSelection(TextRange(start))
        folds.inline.open(TextRange(start, end))
        assertNull(folds.inline.collapsed)
        state.setSelection(TextRange(0))
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
    }

    @Test
    fun typingAtAFoldOpensItUntilTheCursorLeavesTheLine() {
        val (state, folds) = foldsOf(text)
        state.setSelection(TextRange(end))
        state.replaceSelection("x")
        folds.inline.scanNow()
        assertNull(folds.inline.collapsed)
        state.setSelection(TextRange(0))
        assertNull(folds.inline.collapsed)
        state.setSelection(TextRange(state.rawText.length))
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
    }

    @Test
    fun whatWasJustTypedStaysOpenUntilTheCursorLeavesTheLine() {
        val typed = "see [x](0123456789abcdef"
        val (state, folds) = foldsOf("$typed\nnext")
        folds.inline.scanNow()
        assertNull(folds.inline.collapsed)
        state.setSelection(TextRange(typed.length))
        state.replaceSelection(")")
        folds.inline.scanNow()
        assertNull(folds.inline.collapsed)
        state.replaceSelection(" and more")
        folds.inline.scanNow()
        assertNull(folds.inline.collapsed)
        state.setSelection(TextRange(state.rawText.length))
        assertArrayEquals(intArrayOf(typed.indexOf('0')), folds.inline.collapsed!!.starts)
    }

    @Test
    fun aRemoteFoldInAnotherLineCollapsesRightAway() {
        val (state, folds) = foldsOf("first\nsecond", 0)
        state.applyRemote(listOf(TextSplice(6, 0, "[x](0123456789abcdef) ")))
        folds.inline.scanNow()
        assertArrayEquals(intArrayOf(10), folds.inline.collapsed!!.starts)
    }

    @Test
    fun aPositionWithinAClosedFoldSkipsToItsEdge() {
        val ranges = ranges(4, ends = intArrayOf(10), placeholders = arrayOf("x"))
        assertEquals(10, ranges.skip(5, forward = true))
        assertEquals(4, ranges.skip(5, forward = false))
        assertEquals(4, ranges.skip(4, forward = true))
        assertEquals(10, ranges.skip(10, forward = false))
        assertEquals(11, ranges.skip(11, forward = true))
    }

    @Test
    fun aWordDoesNotReachIntoAClosedFold() {
        val ranges = ranges(4, ends = intArrayOf(10), placeholders = arrayOf("x"))
        assertEquals(TextRange(0, 4), ranges.visiblePartOf(4, TextRange(0, 10)))
        assertEquals(TextRange(10, 14), ranges.visiblePartOf(10, TextRange(4, 14)))
        assertEquals(TextRange(4), ranges.visiblePartOf(4, TextRange(4, 10)))
    }

    @Test
    fun aSelectionOverAWholeFoldKeepsItCollapsed() {
        val (state, folds) = foldsOf(text)
        state.setSelection(TextRange(0, text.length))
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
        state.setSelection(TextRange(start, end))
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
    }

    @Test
    fun aSelectionThatEndsWithinAFoldOpensIt() {
        val (state, folds) = foldsOf(text)
        state.setSelection(TextRange(0, start + 2))
        assertNull(folds.inline.collapsed)
    }

    @Test
    fun movingTheCursorKeepsTheSameRanges() {
        val (state, folds) = foldsOf(text)
        val before = folds.inline.collapsed
        state.setSelection(TextRange(text.length - 1))
        assertSame(before, folds.inline.collapsed)
    }

    @Test
    fun foldsMoveWithAChangeInFrontOfThem() {
        val (state, folds) = foldsOf(text, 0)
        state.replaceSelection("xyz")
        folds.inline.scanNow()
        state.setSelection(TextRange(state.rawText.length))
        assertArrayEquals(intArrayOf(start + 3), folds.inline.collapsed!!.starts)
        assertArrayEquals(intArrayOf(end + 3), folds.inline.collapsed!!.ends)
    }

    @Test
    fun aRemoteChangeWithinAFoldKeepsItCollapsed() {
        val (state, folds) = foldsOf(text)
        state.applyRemote(listOf(TextSplice(start + 2, 2, "")))
        folds.inline.scanNow()
        assertArrayEquals(intArrayOf(start), folds.inline.collapsed!!.starts)
        assertArrayEquals(intArrayOf(end - 2), folds.inline.collapsed!!.ends)
    }

    @Test
    fun aLineBreakWithinAFoldRemovesIt() {
        val (state, folds) = foldsOf(text)
        state.applyRemote(listOf(TextSplice(start + 2, 0, "\n")))
        folds.inline.scanNow()
        assertNull(folds.inline.collapsed)
    }

    @Test
    fun aFoldThatTheProviderGetsWrongIsIgnored() {
        val state = KodeEditorState("ab\ncd😀")
        state.setSelection(TextRange(0))
        val provider = object : KodeEditorFoldProvider {
            override fun foldRegions(text: String) = emptyList<FoldRegion>()
            override fun inlineFolds(text: String) = listOf(
                // over a line break, out of the text, empty, within a surrogate pair, line break in the placeholder
                InlineFold(1, 4, "x"),
                InlineFold(3, 99, "x"),
                InlineFold(4, 4, "x"),
                InlineFold(4, 6, "x"),
                InlineFold(3, 4, "x\ny"),
            )
        }
        assertNull(EditorInlineFolds(state, provider).collapsed)
    }

    @Test
    fun theOuterOfOverlappingFoldsWins() {
        val state = KodeEditorState("0123456789")
        val provider = object : KodeEditorFoldProvider {
            override fun foldRegions(text: String) = emptyList<FoldRegion>()
            override fun inlineFolds(text: String) = listOf(InlineFold(3, 4, "a"), InlineFold(2, 6, "b"), InlineFold(5, 8, "c"))
        }
        state.setSelection(TextRange(0))
        val collapsed = EditorInlineFolds(state, provider).collapsed!!
        assertArrayEquals(intArrayOf(2), collapsed.starts)
        assertEquals("b", collapsed.placeholders.single())
    }

    @Test
    fun positionsTranslateBetweenTextAndRow() {
        // "ab123456gh78k" is shown as "abXghYYk"
        val ranges = ranges(2, 10, ends = intArrayOf(8, 12), placeholders = arrayOf("X", "YY"))
        val row = ranges.inRow(0, 13)!!
        assertEquals("abXghYYk", row.shownText("ab123456gh78k", 13))
        assertEquals(2, row.toShown(2))
        assertEquals(2, row.toShown(5))
        assertEquals(3, row.toShown(8))
        assertEquals(5, row.toShown(10))
        assertEquals(7, row.toShown(12))
        assertEquals(2, row.toText(2))
        assertEquals(8, row.toText(3))
        assertEquals(10, row.toText(5))
        assertEquals(10, row.toText(5))
        assertEquals(12, row.toText(6))
        assertEquals(12, row.toText(7))
    }

    @Test
    fun aRowOnlyHasTheFoldsCompletelyWithinIt() {
        val ranges = ranges(2, 10, ends = intArrayOf(8, 12), placeholders = arrayOf("X", "Y"))
        assertNull(ranges.inRow(0, 2))
        assertNull(ranges.inRow(0, 5))
        assertEquals(0, ranges.inRow(0, 8)!!.first)
        assertEquals(0, ranges.inRow(0, 8)!!.last)
        assertEquals(1, ranges.inRow(8, 20)!!.first)
    }

    @Test
    fun aBreakWithinAPlaceholderMovesItToTheNextRow() {
        val ranges = ranges(4, ends = intArrayOf(20), placeholders = arrayOf("abcdef"))
        val row = ranges.inRow(0, 24)!!
        // the shown text breaks in the middle of the placeholder
        assertArrayEquals(intArrayOf(4), row.breaksInText(intArrayOf(7), 24))
        // at the start of the row it can not move any further
        val atStart = ranges(0, ends = intArrayOf(20), placeholders = arrayOf("abcdef")).inRow(0, 24)!!
        assertArrayEquals(intArrayOf(20), atStart.breaksInText(intArrayOf(3), 24))
        // behind it nothing changes
        assertArrayEquals(intArrayOf(21), row.breaksInText(intArrayOf(11), 24))
    }

    @Test
    fun changesAreWhatIsOnlyInOneOfTheTwo() {
        val first = ranges(2, 10, ends = intArrayOf(8, 12), placeholders = arrayOf("X", "Y"))
        val second = ranges(10, 20, ends = intArrayOf(12, 22), placeholders = arrayOf("Y", "Z"))
        assertEquals(listOf(TextRange(2, 8), TextRange(20, 22)), first.changesTo(second))
        assertEquals(listOf(TextRange(2, 8), TextRange(10, 12)), first.changesTo(null))
    }
}
