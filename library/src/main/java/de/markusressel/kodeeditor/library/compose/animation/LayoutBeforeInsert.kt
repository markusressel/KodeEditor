package de.markusressel.kodeeditor.library.compose.animation

import androidx.compose.ui.geometry.Offset

/**
 * Where the text was before a change, an insertion, a removal or both, as seen from the text after it, which is all it
 * uses besides the removed text. Before an insertion, the text behind it was where the inserted text starts now. Before
 * a removal, it was behind the removed text.
 *
 * @param text the text after the change
 * @param lineStarts where the lines of [text] start
 * @param start where the text changed
 * @param length how long the inserted text is
 * @param firstRows the first row of each line before, as shown, and the number of rows at the end
 * @param breaks the positions where each line broke before, relative to its start
 * @param lineHeight the height of a row
 * @param measure the width of a part of [text] in a row, or of a part of [removed]
 * @param removed the text that was removed at [start]
 */
internal class LayoutBeforeInsert(
    private val text: String,
    private val lineStarts: IntArray,
    private val start: Int,
    private val length: Int,
    private val firstRows: IntArray,
    private val breaks: Array<IntArray?>,
    private val lineHeight: Float,
    private val measure: (text: String, start: Int, end: Int) -> Float,
    private val removed: String = "",
) : LayoutBefore {
    override val keepsPlaces get() = true

    private val end = start + length
    private val removedEnd = start + removed.length
    private val firstLine = lineOf(start)
    private val insertedLines = text.substring(start, end).count { it == '\n' }
    private val removedLines = removed.count { it == '\n' }

    private fun lineOf(offset: Int): Int {
        var low = 0
        var high = lineStarts.size - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (lineStarts[middle] <= offset) low = middle else high = middle - 1
        }
        return low
    }

    /** @return the position in the text before the change, the inserted text is where it was inserted */
    override fun toBefore(offset: Int): Int = when {
        offset < start -> offset
        offset < end -> start
        else -> offset - length + removed.length
    }

    /** @return the position in [text] of a position before the change, removed text is where it was removed */
    private fun fromBefore(offset: Int): Int = when {
        offset < start -> offset
        offset < removedEnd -> start
        else -> offset - removed.length + length
    }

    /** @return the line of a position in the text before the change */
    private fun lineBefore(offset: Int): Int = when {
        offset < start -> lineOf(offset)
        offset < removedEnd -> firstLine + removed.substring(0, offset - start).count { it == '\n' }
        else -> lineOf(fromBefore(offset)) - insertedLines + removedLines
    }

    /** @return where a line before the change started, in the text before it */
    private fun startBefore(line: Int): Int = when {
        line <= firstLine -> lineStarts[line]
        line <= firstLine + removedLines -> {
            // a line that started in the removed text
            var index = -1
            repeat(line - firstLine) { index = removed.indexOf('\n', index + 1) }
            start + index + 1
        }

        else -> toBefore(lineStarts[line - removedLines + insertedLines])
    }

    /** @return the width of a part of the text before the change, measured in [text] and [removed] */
    private fun measureBefore(from: Int, to: Int): Float {
        if (from >= to) return 0f
        var width = 0f
        if (from < start) width += measure(text, from, minOf(to, start))
        if (to > start && from < removedEnd) {
            width += measure(removed, maxOf(from, start) - start, minOf(to, removedEnd) - start)
        }
        if (to > removedEnd) {
            val shift = length - removed.length
            width += measure(text, maxOf(from, removedEnd) + shift, to + shift)
        }
        return width
    }

    /** @return where the text at [offset] of [text] was before the change, in the coordinates of the document */
    override fun positionOf(offset: Int): Offset {
        val before = toBefore(offset)
        val line = lineBefore(before)
        val lineStart = startBefore(line)
        val lineBreaks = breaks.getOrNull(line)
        val index = lineBreaks?.count { it <= before - lineStart } ?: 0
        val rowStart = lineStart + if (index == 0) 0 else lineBreaks!![index - 1]
        val row = firstRows[line.coerceAtMost(firstRows.size - 1)] + index
        return Offset(measureBefore(rowStart, before), row * lineHeight)
    }

    /** @return where the [line] of [text] broke before the change, relative to its start */
    override fun breaksOf(line: Int): IntArray? {
        if (line < firstLine) return breaks.getOrNull(line)
        if (line > firstLine + insertedLines) return breaks.getOrNull(line - insertedLines + removedLines)
        // the lines of the change: the breaks and the starts of the lines it joined, where the rows started before
        val lineStart = lineStarts[line]
        val lineEnd = lineStarts.getOrNull(line + 1) ?: (text.length + 1)
        val result = ArrayList<Int>()
        for (before in firstLine..firstLine + removedLines) {
            val beforeStart = startBefore(before)
            if (before > firstLine) result += fromBefore(beforeStart)
            breaks.getOrNull(before)?.forEach { result += fromBefore(beforeStart + it) }
        }
        return result.filter { it in lineStart until lineEnd }.distinct().sorted().map { it - lineStart }
            .toIntArray().takeIf { it.isNotEmpty() }
    }
}
