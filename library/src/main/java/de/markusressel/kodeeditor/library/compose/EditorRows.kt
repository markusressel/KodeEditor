package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs

/**
 * What the editor shows on one height of a line. Without line wrapping each line is one row. With it, each row is a
 * part of a line, so all rows keep the same height, and the rows of the visible area can be found without measuring
 * the text before them.
 */
internal interface EditorRows {
    val count: Int

    /** the row of a position in the text. A position between two rows of a line belongs to the second one */
    fun rowOf(offset: Int): Int

    /** the line a row is a part of */
    fun lineOf(row: Int): Int

    /** the first row of a line */
    fun firstRow(line: Int): Int

    /** the last row of a line */
    fun lastRow(line: Int): Int

    /** where the row starts in the text */
    fun start(row: Int): Int

    /** where the row ends in the text, in front of the line break */
    fun end(row: Int): Int
}

/** each line is one row */
internal class UnwrappedRows(private val state: KodeEditorState) : EditorRows {
    override val count get() = state.lineCount
    override fun rowOf(offset: Int) = state.lineOf(offset)
    override fun lineOf(row: Int) = row
    override fun firstRow(line: Int) = line
    override fun lastRow(line: Int) = line
    override fun start(row: Int) = state.lineStart(row)
    override fun end(row: Int) = state.lineEnd(row)
}

/**
 * The rows of a text with wrapped lines. Each line knows the positions where it breaks, relative to its start, so a
 * change of the text only needs the rows of the changed lines again.
 *
 * @param breaker finds the positions where a line of the text breaks at a width, null if the line fits
 */
internal class WrappedRows(
    private val state: KodeEditorState,
    private val breaker: (text: String, start: Int, end: Int, width: Float) -> IntArray?,
) : EditorRows {
    private var width = 0f
    private var version = -1
    private var breaks = arrayOfNulls<IntArray>(0)

    /** the first row of each line, and the number of rows at the end */
    private var firstRows = IntArray(1)

    /** the rows before the width changed, until someone asked for the new place of a position in them */
    private var rowsBeforeResize: PreviousRows? = null

    override val count get() = firstRows[breaks.size]

    /** Brings the rows up to date, which is cheap if nothing changed */
    fun update(width: Float) {
        if (width != this.width) {
            if (this.width > 0f && rowsBeforeResize == null) rowsBeforeResize = PreviousRows(firstRows, breaks)
            this.width = width
            rebuild()
        } else if (version != state.rawVersion) {
            rebuild()
        }
    }

    /** the text changed, the line index of the state is up to date already */
    fun onSplice(splice: TextSplice) {
        if (version == -1) return
        val inserted = splice.inserted.count { it == '\n' }
        val newCount = state.lineCount
        val removed = breaks.size - newCount + inserted
        val firstLine = state.lineOf(splice.start)
        val lineCountChanged = newCount != breaks.size
        val next = if (lineCountChanged) arrayOfNulls<IntArray>(newCount) else breaks
        if (lineCountChanged) {
            System.arraycopy(breaks, 0, next, 0, firstLine)
            val tail = firstLine + 1 + removed
            System.arraycopy(breaks, tail, next, firstLine + 1 + inserted, breaks.size - tail)
        }
        var rowsChanged = lineCountChanged
        for (line in firstLine..firstLine + inserted) {
            val before = next[line]
            val after = breaksOf(line)
            next[line] = after
            if ((before?.size ?: 0) != (after?.size ?: 0)) rowsChanged = true
        }
        breaks = next
        if (rowsChanged) countRows()
        version = state.rawVersion
    }

    private fun rebuild() {
        val lines = state.lineCount
        breaks = Array(lines) { breaksOf(it) }
        countRows()
        version = state.rawVersion
    }

    private fun breaksOf(line: Int) = breaker(state.rawText, state.lineStart(line), state.lineEnd(line), width)

    private fun countRows() {
        val lines = breaks.size
        val rows = IntArray(lines + 1)
        var row = 0
        for (line in 0 until lines) {
            rows[line] = row
            row += 1 + (breaks[line]?.size ?: 0)
        }
        rows[lines] = row
        firstRows = rows
    }

    override fun rowOf(offset: Int): Int {
        val line = state.lineOf(offset)
        return firstRows[line] + rowInLine(breaks[line], offset - state.lineStart(line))
    }

    override fun lineOf(row: Int) = lineOf(firstRows, breaks.size, row)

    override fun firstRow(line: Int) = firstRows[line]

    override fun lastRow(line: Int) = firstRows[line + 1] - 1

    override fun start(row: Int): Int {
        val line = lineOf(row)
        val index = row - firstRows[line]
        return state.lineStart(line) + if (index == 0) 0 else breaks[line]!![index - 1]
    }

    override fun end(row: Int): Int {
        val line = lineOf(row)
        val index = row - firstRows[line]
        val lineBreaks = breaks[line]
        return if (lineBreaks == null || index == lineBreaks.size) state.lineEnd(line)
        else state.lineStart(line) + lineBreaks[index]
    }

    /**
     * @param rowPosition a position in rows before the width changed
     * @return the position of the same text in the current rows, null if the width did not change since the last call
     */
    fun takePositionAfterResize(rowPosition: Float): Float? {
        val previous = rowsBeforeResize ?: return null
        rowsBeforeResize = null
        val row = rowPosition.toInt().coerceIn(0, previous.firstRows.last() - 1)
        val line = lineOf(previous.firstRows, previous.breaks.size, row)
        val index = row - previous.firstRows[line]
        val inLine = if (index == 0) 0 else previous.breaks[line]!![index - 1]
        val newLine = line.coerceAtMost(breaks.size - 1)
        return firstRows[newLine] + rowInLine(breaks[newLine], inLine) + (rowPosition - row)
    }

    private class PreviousRows(val firstRows: IntArray, val breaks: Array<IntArray?>)

    private companion object {
        fun lineOf(firstRows: IntArray, lines: Int, row: Int): Int {
            var low = 0
            var high = lines - 1
            while (low < high) {
                val middle = (low + high + 1) ushr 1
                if (firstRows[middle] <= row) low = middle else high = middle - 1
            }
            return low
        }

        fun rowInLine(breaks: IntArray?, inLine: Int): Int {
            breaks ?: return 0
            var row = 0
            while (row < breaks.size && breaks[row] <= inLine) row++
            return row
        }
    }
}

/**
 * Finds where lines break. In a monospaced font this is simple arithmetic for most text, which keeps wrapping a long
 * document fast. Everything else is measured.
 *
 * @param measurer measures lines, this should not be the one that keeps the drawn lines in its cache
 * @param textStyle the style of the text
 */
internal class RowBreaker(private val measurer: TextMeasurer, private val textStyle: TextStyle) {
    private val cellWidth = advanceOf("M")
    private val monospaced = cellWidth > 0f && abs(advanceOf("i") - cellWidth) < 0.01f

    private fun advanceOf(character: String): Float {
        val text = character.repeat(REFERENCE_LENGTH)
        val width = measurer.measure(text, style = textStyle, softWrap = false)
            .getHorizontalPosition(REFERENCE_LENGTH, usePrimaryDirection = true)
        return width / REFERENCE_LENGTH
    }

    /** @return the positions where the line breaks relative to [start], null if the line fits into one row */
    fun breaks(text: String, start: Int, end: Int, width: Float): IntArray? {
        val length = end - start
        if (length == 0 || width <= 0f) return null
        if (!monospaced) return measuredBreaks(text, start, end, width)
        val cells = (width / cellWidth).toInt().coerceAtLeast(1)
        // no character is wider than two cells
        if (length * 2 <= cells) return null
        for (index in start until end) {
            if (!text[index].fillsOneCell()) return measuredBreaks(text, start, end, width)
        }
        return cellBreaks(text, start, end, cells)
    }

    private fun measuredBreaks(text: String, start: Int, end: Int, width: Float): IntArray? {
        val result = measurer.measure(
            text.substring(start, end),
            style = textStyle,
            overflow = TextOverflow.Clip,
            softWrap = true,
            constraints = Constraints(maxWidth = width.toInt().coerceAtLeast(1)),
        )
        if (result.lineCount <= 1) return null
        return IntArray(result.lineCount - 1) { result.getLineEnd(it) }
    }

    private companion object {
        const val REFERENCE_LENGTH = 64
    }
}

/** the letters of most languages in the west and common punctuation, not tabs, wide or combining characters */
private fun Char.fillsOneCell() = this in ' '..'˿' || this in '‐'..'‧'

/**
 * Breaks a line of characters that all have the same width after a space or a hyphen, and within a word that does not
 * fit into a row at all.
 *
 * @param cells the number of characters that fit into a row
 * @return the positions where the line breaks relative to [start], null if the line fits into one row
 */
internal fun cellBreaks(text: String, start: Int, end: Int, cells: Int): IntArray? {
    if (end - start <= cells) return null
    var result = IntArray(4)
    var size = 0
    var rowStart = start
    while (end - rowStart > cells) {
        val limit = rowStart + cells
        var next = limit
        while (next > rowStart + 1 && text[next - 1] != ' ' && text[next - 1] != '-') next--
        if (next == rowStart + 1 && text[rowStart] != ' ' && text[rowStart] != '-') next = limit
        if (size == result.size) result = result.copyOf(size * 2)
        result[size++] = next - start
        rowStart = next
    }
    return result.copyOf(size)
}
