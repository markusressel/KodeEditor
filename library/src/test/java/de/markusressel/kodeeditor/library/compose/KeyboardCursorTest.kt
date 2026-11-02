package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardCursorTest {

    /** every character is 10 wide and every line 20 high */
    private class MonospacedLines(text: String) : TextLines {
        private val starts = listOf(0) + text.indices.filter { text[it] == '\n' }.map { it + 1 }
        private val length = text.length

        override val lineCount = starts.size
        override fun lineOf(offset: Int) = starts.indexOfLast { it <= offset }
        override fun lineAt(y: Float) = (y / 20f).toInt().coerceIn(0, lineCount - 1)
        override fun topOf(line: Int) = line * 20f
        override fun xOf(offset: Int) = (offset - starts[lineOf(offset)]) * 10f
        override fun offsetAt(line: Int, x: Float): Int {
            val end = if (line + 1 < lineCount) starts[line + 1] - 1 else length
            return (starts[line] + (x / 10f).toInt()).coerceAtMost(end)
        }
    }

    private val cursor = KeyboardCursor()

    private fun move(
        text: String,
        selection: TextRange,
        move: CursorMove,
        extend: Boolean = false,
        pageHeight: Float = 60f,
    ) = cursor.move(text, selection, move, extend, MonospacedLines(text), pageHeight)

    private fun move(text: String, offset: Int, move: CursorMove, extend: Boolean = false) =
        move(text, TextRange(offset), move, extend)

    @Test
    fun leftAndRightMoveOneCharacter() {
        assertEquals(TextRange(1), move("abc", 2, CursorMove.CharacterLeft))
        assertEquals(TextRange(3), move("abc", 2, CursorMove.CharacterRight))
    }

    @Test
    fun theEndsOfTheTextAreTheLimit() {
        assertEquals(TextRange(0), move("abc", 0, CursorMove.CharacterLeft))
        assertEquals(TextRange(3), move("abc", 3, CursorMove.CharacterRight))
        assertEquals(TextRange(0), move("abc", 0, CursorMove.WordLeft))
        assertEquals(TextRange(3), move("abc", 3, CursorMove.WordRight))
    }

    @Test
    fun anEmojiCountsAsOneCharacter() {
        val text = "a😀b"
        assertEquals(TextRange(3), move(text, 1, CursorMove.CharacterRight))
        assertEquals(TextRange(1), move(text, 3, CursorMove.CharacterLeft))
    }

    @Test
    fun aSelectionCollapsesToItsSide() {
        assertEquals(TextRange(1), move("abcde", TextRange(1, 3), CursorMove.CharacterLeft))
        assertEquals(TextRange(3), move("abcde", TextRange(1, 3), CursorMove.CharacterRight))
        assertEquals(TextRange(1), move("abcde", TextRange(3, 1), CursorMove.CharacterLeft))
        assertEquals(TextRange(3), move("abcde", TextRange(3, 1), CursorMove.CharacterRight))
    }

    @Test
    fun shiftMovesTheEndOfTheSelection() {
        assertEquals(TextRange(2, 3), move("abcde", 2, CursorMove.CharacterRight, extend = true))
        assertEquals(TextRange(1, 4), move("abcde", TextRange(1, 3), CursorMove.CharacterRight, extend = true))
        assertEquals(TextRange(3, 0), move("abcde", TextRange(3, 1), CursorMove.CharacterLeft, extend = true))
    }

    @Test
    fun wordsSkipWhatIsBetweenThem() {
        val text = "foo, bar_baz\nüber"
        assertEquals(TextRange(3), move(text, 0, CursorMove.WordRight))
        assertEquals(TextRange(12), move(text, 3, CursorMove.WordRight))
        assertEquals(TextRange(17), move(text, 12, CursorMove.WordRight))
        assertEquals(TextRange(13), move(text, 17, CursorMove.WordLeft))
        assertEquals(TextRange(5), move(text, 13, CursorMove.WordLeft))
        assertEquals(TextRange(0), move(text, 5, CursorMove.WordLeft))
    }

    @Test
    fun upAndDownKeepTheColumn() {
        val text = "first line\nab\nthird line"
        assertEquals(TextRange(13), move(text, 7, CursorMove.LineDown))
        // the short line in the middle does not change the column
        assertEquals(TextRange(21), move(text, 13, CursorMove.LineDown))
        assertEquals(TextRange(13), move(text, 21, CursorMove.LineUp))
        assertEquals(TextRange(7), move(text, 13, CursorMove.LineUp))
    }

    @Test
    fun anotherMoveForgetsTheColumn() {
        val text = "first line\nab\nthird line"
        assertEquals(TextRange(13), move(text, 7, CursorMove.LineDown))
        assertEquals(TextRange(12), move(text, 13, CursorMove.CharacterLeft))
        assertEquals(TextRange(15), move(text, 12, CursorMove.LineDown))
    }

    @Test
    fun aMovedCursorForgetsTheColumn() {
        val text = "first line\nab\nthird line"
        assertEquals(TextRange(13), move(text, 7, CursorMove.LineDown))
        // moved by a tap or a remote change
        assertEquals(TextRange(1), move(text, 12, CursorMove.LineUp))
    }

    @Test
    fun upInTheFirstLineGoesToTheStartAndDownInTheLastToTheEnd() {
        val text = "abc\ndef"
        assertEquals(TextRange(0), move(text, 2, CursorMove.LineUp))
        assertEquals(TextRange(7), move(text, 5, CursorMove.LineDown))
    }

    @Test
    fun aSelectionCollapsesBeforeMovingALine() {
        val text = "abc\ndef\nghi"
        assertEquals(TextRange(1), move(text, TextRange(5, 6), CursorMove.LineUp))
        assertEquals(TextRange(10), move(text, TextRange(5, 6), CursorMove.LineDown))
    }

    @Test
    fun shiftExtendsAcrossLines() {
        assertEquals(TextRange(1, 5), move("abc\ndef", 1, CursorMove.LineDown, extend = true))
    }

    @Test
    fun paragraphsMoveToTheirEnds() {
        val text = "abc\ndef\nghi"
        assertEquals(TextRange(3), move(text, 1, CursorMove.ParagraphDown))
        assertEquals(TextRange(7), move(text, 3, CursorMove.ParagraphDown))
        assertEquals(TextRange(11), move(text, 7, CursorMove.ParagraphDown))
        assertEquals(TextRange(8), move(text, 9, CursorMove.ParagraphUp))
        assertEquals(TextRange(4), move(text, 8, CursorMove.ParagraphUp))
        assertEquals(TextRange(0), move(text, 4, CursorMove.ParagraphUp))
        assertEquals(TextRange(0), move(text, 0, CursorMove.ParagraphUp))
    }

    @Test
    fun pagesMoveByTheVisibleHeightAndKeepTheColumn() {
        val text = (0..9).joinToString("\n") { "line$it" } // 5 characters per line, 6 with the line break
        assertEquals(TextRange(3 * 6 + 2), move(text, 2, CursorMove.PageDown))
        assertEquals(TextRange(2), move(text, 3 * 6 + 2, CursorMove.PageUp))
    }

    @Test
    fun aPageAtTheEndsOfTheTextStopsInTheLastAndFirstLine() {
        val text = "abc\ndef\nghi"
        assertEquals(TextRange(9), move(text, 1, CursorMove.PageDown))
        assertEquals(TextRange(1), move(text, 9, CursorMove.PageUp))
    }

    @Test
    fun keysMapToCommands() {
        fun command(key: Key, ctrl: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
            keyboardCommandFor(key, ctrl, alt, meta)

        assertEquals(KeyboardCommand.Move(CursorMove.CharacterLeft), command(Key.DirectionLeft))
        assertEquals(KeyboardCommand.Move(CursorMove.WordRight), command(Key.DirectionRight, ctrl = true))
        assertEquals(KeyboardCommand.Move(CursorMove.WordLeft), command(Key.DirectionLeft, alt = true))
        assertEquals(KeyboardCommand.Move(CursorMove.ParagraphUp), command(Key.DirectionUp, ctrl = true))
        assertEquals(KeyboardCommand.Move(CursorMove.PageDown), command(Key.PageDown))
        assertEquals(KeyboardCommand.ZoomIn, command(Key.Plus, ctrl = true))
        assertEquals(KeyboardCommand.ZoomIn, command(Key.Equals, ctrl = true))
        assertEquals(KeyboardCommand.ZoomOut, command(Key.Minus, ctrl = true))
        assertEquals(KeyboardCommand.ZoomReset, command(Key.Zero, ctrl = true))

        assertEquals(KeyboardCommand.InsertTab, command(Key.Tab))
        assertNull(keyboardCommandFor(Key.Tab, ctrl = false, alt = false, meta = false, shift = true))
        assertNull(command(Key.Tab, ctrl = true))

        assertNull(command(Key.Plus))
        assertEquals(KeyboardCommand.SelectAll, command(Key.A, ctrl = true))
        assertNull(command(Key.A))
        assertNull(command(Key.DirectionLeft, meta = true))
        assertNull(command(Key.PageDown, ctrl = true))
    }
}
