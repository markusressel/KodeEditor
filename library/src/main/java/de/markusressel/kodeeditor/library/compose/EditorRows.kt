package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.ceil

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
 * @param onReflow called with the rows before a change of the width that made lines break differently
 * @param sameBreaks whether two widths are known to break all lines at the same positions, which saves going through them
 * @param hiddenLines the lines that folds hide. These rows still have them, positions from outside don't count them
 * @param breaker finds the positions where a line of the text breaks at a width, null if the line fits
 */
internal class WrappedRows(
    private val state: KodeEditorState,
    private val onReflow: (firstRows: IntArray, breaks: Array<IntArray?>) -> Unit = { _, _ -> },
    private val sameBreaks: (first: Float, second: Float) -> Boolean = { _, _ -> false },
    private val hiddenLines: () -> HiddenLines? = { null },
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

    /** the first row of each line, and the number of rows at the end */
    val currentFirstRows: IntArray get() = firstRows

    /** the positions where each line breaks */
    val currentBreaks: Array<IntArray?> get() = breaks

    /** changes when the rows may have changed */
    var generation = 0
        private set

    /** Brings the rows up to date, which is cheap if nothing changed */
    fun update(width: Float) {
        if (width != this.width) {
            if (this.width > 0f && version == state.rawVersion && sameBreaks(this.width, width)) {
                this.width = width
                return
            }
            if (this.width > 0f && rowsBeforeResize == null) rowsBeforeResize = PreviousRows(firstRows, breaks)
            val before = breaks
            val firstRowsBefore = firstRows
            val hadWidth = this.width > 0f && version == state.rawVersion
            this.width = width
            rebuild()
            if (hadWidth && !before.contentDeepEquals(breaks)) onReflow(firstRowsBefore, before)
        } else if (version != state.rawVersion) {
            rebuild()
        }
    }

    /**
     * The text changed, the line index of the state is up to date already.
     *
     * @param splice the change
     * @param settledWidth the width after the change if it is not meant to be seen as a movement, which saves going
     * through the lines for the old width first. Null keeps the width until the next [update]
     */
    fun onSplice(splice: TextSplice, settledWidth: Float? = null) {
        if (version == -1) return
        if (settledWidth != null && settledWidth != width) {
            width = settledWidth
            rowsBeforeResize = null
            rebuild()
            return
        }
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
        generation++
        if (rowsChanged) countRows()
        version = state.rawVersion
    }

    private fun rebuild() {
        val lines = state.lineCount
        breaks = Array(lines) { breaksOf(it) }
        generation++
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
     * @param anchors positions in the text that should stay where they are, the first visible one is used
     * @param visibleRows how many rows are visible below the [rowPosition]
     * @return the position that keeps the first visible anchor in its place, otherwise the position of the same text at
     * [rowPosition], null if the width did not change since the last call
     */
    fun takePositionAfterResize(
        rowPosition: Float,
        anchors: List<Int> = emptyList(),
        visibleRows: Float = 0f,
    ): Float? {
        val previous = rowsBeforeResize ?: return null
        rowsBeforeResize = null
        val hidden = hiddenLines()
        val firstRowsBefore = hidden?.shownFirstRows(previous.firstRows) ?: previous.firstRows
        val firstRowsNow = hidden?.shownFirstRows(firstRows) ?: firstRows
        if (previous.breaks.size == breaks.size) {
            anchors.forEach { anchor ->
                val line = state.lineOf(anchor)
                val inLine = anchor - state.lineStart(line)
                val rowBefore = firstRowsBefore[line] + rowInLine(previous.breaks[line], inLine)
                if (rowBefore + 1 > rowPosition && rowBefore < rowPosition + visibleRows) {
                    return rowPosition + firstRowsNow[line] + rowInLine(breaks[line], inLine) - rowBefore
                }
            }
        }
        val row = rowPosition.toInt().coerceIn(0, firstRowsBefore.last() - 1)
        val line = lineOf(firstRowsBefore, previous.breaks.size, row)
        val index = row - firstRowsBefore[line]
        val inLine = if (index == 0) 0 else previous.breaks[line]!![index - 1]
        val newLine = line.coerceAtMost(breaks.size - 1)
        return firstRowsNow[newLine] + rowInLine(breaks[newLine], inLine) + (rowPosition - row)
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

    /** the text that [checkedLines] and [cellLines] are about, which only have to be found once for it */
    private var checkedText: String? = null
    private val checkedLines = BitSet()
    private val cellLines = BitSet()
    private val emojiLines = BitSet()

    /** how many cells an emoji takes, only asked for once there is one, because this loads its font */
    private val emojiCells by lazy {
        ceil(advanceOf("\uD83D\uDE00") / cellWidth - CELL_TOLERANCE).toInt().coerceAtLeast(1)
    }

    /** a measured line can break differently at any width */
    private var measuredLines = false

    /** @return true if lines are known to break at the same positions at both widths */
    fun breakAlike(first: Float, second: Float) =
        monospaced && !measuredLines && first > 0f && second > 0f && cellsOf(first) == cellsOf(second)

    private fun cellsOf(width: Float) = (width / cellWidth).toInt().coerceAtLeast(1)

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
        val cells = cellsOf(width)
        // no character is wider than two cells
        if (length * 2 <= cells) return null
        return when {
            fillsCells(text, start, end) -> cellBreaks(text, start, end, cells)
            emojiLines.get(start) -> emojiBreaks(text, start, end, cells, emojiCells)
            else -> measuredBreaks(text, start, end, width)
        }
    }

    /** A change of the width goes through all lines again, what their characters are stays the same for it */
    private fun fillsCells(text: String, start: Int, end: Int): Boolean {
        if (text !== checkedText) {
            checkedText = text
            checkedLines.clear()
            cellLines.clear()
            emojiLines.clear()
        }
        if (checkedLines.get(start)) return cellLines.get(start)
        var fills = true
        var countable = true
        for (index in start until end) {
            val character = text[index]
            if (!character.fillsOneCell()) {
                fills = false
                if (!character.isPartOfEmoji()) {
                    countable = false
                    break
                }
            }
        }
        checkedLines.set(start)
        cellLines.set(start, fills)
        emojiLines.set(start, !fills && countable)
        return fills
    }

    private fun measuredBreaks(text: String, start: Int, end: Int, width: Float): IntArray? {
        measuredLines = true
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
        const val CELL_TOLERANCE = 0.01f
    }
}

/** the letters of most languages in the west and common punctuation, not tabs, wide or combining characters */
private fun Char.fillsOneCell() = this in ' '..'˿' || this in '‐'..'‧'

private const val ZERO_WIDTH_JOINER = '\u200D'
private const val VARIATION_SELECTOR_EMOJI = '\uFE0F'
private const val COMBINING_KEYCAP = '\u20E3'

/** takes no room of its own, it changes what is in front of it */
private fun Char.joinsEmoji() =
    this == ZERO_WIDTH_JOINER || this == VARIATION_SELECTOR_EMOJI || this == COMBINING_KEYCAP

/** emojis, and symbols that can be shown as one. Characters outside of the basic plane are wide too, if they are not */
private fun Char.isPartOfEmoji() = isSurrogate() || joinsEmoji() || this in '\u2190'..'\u2BFF'

/**
 * Like [cellBreaks], for a line that also has emojis. Each row is drawn on its own, so a break only has to make the row
 * fit. Emojis that are shown as a single one are counted one by one, those rows just end a bit earlier.
 *
 * @param cells the number of characters that fit into a row
 * @param emojiCells the number of cells an emoji takes
 * @return the positions where the line breaks relative to [start], null if the line fits into one row
 */
internal fun emojiBreaks(text: String, start: Int, end: Int, cells: Int, emojiCells: Int): IntArray? {
    var result = IntArray(4)
    var size = 0
    var rowStart = start
    var used = 0
    // behind the last space or hyphen of the row
    var afterSeparator = -1
    var index = start
    while (index < end) {
        val character = text[index]
        val width = when {
            character.fillsOneCell() -> 1
            character.isLowSurrogate() || character.joinsEmoji() -> 0
            else -> emojiCells
        }
        if (width > 0 && used + width > cells && index > rowStart) {
            val next = if (afterSeparator > rowStart) afterSeparator else keepEmojiTogether(text, rowStart, index)
            if (size == result.size) result = result.copyOf(size * 2)
            result[size++] = next - start
            rowStart = next
            index = next
            used = 0
            afterSeparator = -1
            continue
        }
        used += width
        index++
        if (character == ' ' || character == '-') afterSeparator = index
    }
    return if (size == 0) null else result.copyOf(size)
}

/** @return where a row can end in front of [position], an emoji made from several stays in one piece if it fits */
private fun keepEmojiTogether(text: String, rowStart: Int, position: Int): Int {
    var index = position
    while (index > rowStart && text[index - 1] == ZERO_WIDTH_JOINER) {
        index--
        // what the joiner connects to
        if (index > rowStart && text[index - 1] == VARIATION_SELECTOR_EMOJI) index--
        if (index > rowStart && text[index - 1].isLowSurrogate()) index--
        if (index > rowStart) index--
    }
    return if (index > rowStart) index else position
}

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
