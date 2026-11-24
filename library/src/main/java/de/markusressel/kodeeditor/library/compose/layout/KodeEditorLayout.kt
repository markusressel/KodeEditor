package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntSize
import de.markusressel.kodeeditor.library.compose.KodeEditor

/**
 * Where the text of a [KodeEditor] is, in the coordinates of the overlay.
 *
 * All rows have the same height and only the rows that are asked for are measured, so this works at any length of
 * the text. A style with a larger font size does not make its row taller. A line is one row, or several rows if the
 * editor wraps its lines.
 *
 * Reading from this in a composable or in a draw phase updates it when the text changes.
 */
@Stable
interface KodeEditorLayout {
    /** the text this describes */
    val text: String

    /** the number of lines */
    val lineCount: Int

    /** the height of each row, which is a whole line unless the editor wraps it */
    val lineHeight: Float

    /** the size of the whole document */
    val contentSize: IntSize

    /**
     * @param offset a position in the text
     * @return the line of that position
     */
    fun getLineForOffset(offset: Int): Int

    /**
     * @param y a vertical position
     * @return the line at that position, the nearest one outside of the text
     */
    fun getLineForVerticalPosition(y: Float): Int

    /**
     * @param line a line
     * @return the position in the text where the line starts
     */
    fun getLineStart(line: Int): Int

    /**
     * @param line a line
     * @return the position in the text where the line ends, in front of its line break
     */
    fun getLineEnd(line: Int): Int

    /**
     * @param line a line
     * @return the top of the first row of the line
     */
    fun getLineTop(line: Int): Float

    /**
     * @param line a line
     * @return the bottom of the last row of the line
     */
    fun getLineBottom(line: Int): Float

    /**
     * @param line a line
     * @return the baseline of the first row of the line
     */
    fun getLineBaseline(line: Int): Float

    /**
     * @param offset a position in the text
     * @return where a cursor at that position is, with a width of zero
     */
    fun getCursorRect(offset: Int): Rect

    /**
     * Only the part of the range near the visible area is included, measuring all lines of a large range would take
     * too long. Read this in a composable or a draw phase to get the part that is needed at the moment.
     *
     * @param start the start of the range
     * @param end the end of the range
     * @return the shape that covers the range
     */
    fun getPathForRange(start: Int, end: Int): Path

    /**
     * @param position a point
     * @return the position in the text closest to the point
     */
    fun getOffsetForPosition(position: Offset): Int

    /**
     * @param offset a position in the text
     * @return the word at that position, a single character if there is no word
     */
    fun getWordBoundary(offset: Int): TextRange

    /**
     * The places of a hidden line and its positions are at the end of the line that hides it.
     *
     * @param line a line
     * @return whether the line is hidden by a fold
     */
    fun isLineHidden(line: Int): Boolean = false

    /**
     * What is folded at a position: a part of a line that a placeholder stands for, or lines a fold hides. Something
     * that reveals text step by step can show such a part as a whole, as it shows no single characters.
     *
     * @param offset a position in the text
     * @return the folded part of the text that has [offset] within it, its start included, null if it is not folded
     */
    fun collapsedRangeAt(offset: Int): TextRange? = null
}
