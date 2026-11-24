package de.markusressel.kodeeditor.library.compose.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.util.lerp
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import kotlin.math.roundToLong

internal const val REFLOW_ANIMATION_MS = 150

/** A change of the wrap width follows the zoom, so a lot of them can be on the way at once */
private const val MAX_RUNNING_REFLOWS = 12

/**
 * Moves the text from where it was before the wrap width changed to where it is now. A reflow that starts during
 * another one starts from where the text is on the screen at that moment.
 *
 * @param state the text, which has to stay the same during the reflow
 * @param firstRows the first row of each line before the change, and the number of rows at the end
 * @param breaks the positions where each line broke before the change
 * @param previous the reflow that was still running when this one started
 * @param lineHeight the height of a row
 * @param measure the width of the text between two positions of the same row
 * @param changedFolds the parts of the text that are shown differently now, what follows them moves on its own
 * @param durationMs how long the text takes to its new place
 * @param easing how the text speeds up and slows down on its way
 * @param inserted where the text was before an insertion, instead of [firstRows], [breaks] and [measure]. Such a
 * transition knows its places without the state, so the next insertion can start from where the text is on its way
 * @param typedOn the insertion this one is right behind, like the key before of somebody who types, also when it is
 * done. Soon after it, see [TYPING_WINDOW_MS], the text moves at an even speed for the time between the two, so typing
 * does not pump
 */
internal class ReflowTransition(
    private val state: KodeEditorState,
    private val firstRows: IntArray,
    private val breaks: Array<IntArray?>,
    previous: ReflowTransition?,
    private val lineHeight: Float,
    private val measure: (start: Int, end: Int) -> Float,
    private val changedFolds: List<TextRange> = emptyList(),
    private var durationMs: Int = REFLOW_ANIMATION_MS,
    private var easing: Easing = FastOutSlowInEasing,
    private val inserted: LayoutBeforeInsert? = null,
    private var typedOn: ReflowTransition? = null,
) {
    // the places of a transition from before a change of the text are about a text that is gone, unless it kept them
    private var previous: ReflowTransition? =
        previous?.takeIf { it.version == state.rawVersion || (inserted != null && it.inserted != null) }

    private val version = state.rawVersion
    private var startMs = -1L

    /** how much later than [startMs] it starts to move, a crisp step starts in its first frame instead of before */
    private var delayMs = 0L
    private var depth: Int = (previous?.depth ?: 0) + 1

    /** stays where it is, an insertion that follows takes the text on from there */
    private var frozen = false

    /** the smoothed time between the keys of somebody who types, see [TYPING_SPAN_FACTOR] */
    private var keyIntervalMs = -1f

    /** where text was before the change, which is the same on each frame */
    private val positions = HashMap<Int, Offset>()

    /** how rows are split, which is also the same on each frame */
    private val rowParts = HashMap<Int, List<RowPart>>()

    /** how far the text moved so far, from 0 to 1 */
    var progress = 0f
        private set

    /** how far the view scrolled because of the reflow, the text must not jump on the screen because of it */
    var scrolled = 0f

    /** how far the view scrolled sideways because of the change, like [scrolled] */
    var scrolledX = 0f

    /** whether the text is still the one this reflow started with */
    val valid: Boolean get() = version == state.rawVersion

    /** @param nowMs the time of the frame */
    fun onFrame(nowMs: Long) {
        if (frozen) return
        val first = startMs < 0
        previous?.let {
            it.onFrame(nowMs)
            // an insertion takes the text on from where it is drawn, instead of adding its move to the one before
            if (first && inserted != null && it.inserted != null) it.frozen = true
            if (it.progress >= 1f || depth > MAX_RUNNING_REFLOWS) previous = null
        }
        val before = typedOn
        // only its time is needed, the transitions do not keep each other
        typedOn = null
        var steady = false
        if (first && before != null && before.startMs >= 0 && durationMs > 0) {
            // the key before started a frame early too, so this is the time between the keys
            val since = nowMs - before.startMs - FRAME_MS
            if (since <= TYPING_WINDOW_MS) {
                keyIntervalMs = smoothedKeyInterval(before.keyIntervalMs, since.toFloat())
                val even = evenStepMs(keyIntervalMs, minOf(MIN_STEP_MS, durationMs.toLong()).toFloat())
                val crisp = crispnessOf(keyIntervalMs)
                durationMs = steadyStepMs(even, crisp).toInt()
                easing = if (crisp <= 0f) LinearEasing else Easing { steadyStepEase(it, crisp) }
                steady = true
                delayMs = (FRAME_MS * crisp).roundToLong()
            }
        }
        // one that takes the text on moves in its first frame already, the text does not stop for a frame at each key
        if (startMs < 0) startMs = if (steady) nowMs - FRAME_MS else nowMs
        val time = ((nowMs - startMs - delayMs) / durationMs.toFloat()).coerceIn(0f, 1f)
        progress = easing.transform(time)
    }

    /** @return where the text at [offset] started this reflow, in the coordinates of the document */
    fun startOf(offset: Int): Offset {
        val position = positions.getOrPut(offset) { positionOf(offset) }
        val before = previous ?: return Offset(position.x + scrolledX, position.y + scrolled)
        val from = before.startOf(inserted?.toBefore(offset) ?: offset)
        return Offset(
            x = lerp(from.x, position.x, before.progress) + scrolledX,
            y = lerp(from.y, position.y, before.progress) + scrolled,
        )
    }

    private fun positionOf(offset: Int): Offset {
        inserted?.let { return it.positionOf(offset) }
        val line = state.lineOf(offset)
        val lineStart = state.lineStart(line)
        val lineBreaks = breaks[line]
        val index = lineBreaks?.count { it <= offset - lineStart } ?: 0
        val rowStart = lineStart + if (index == 0) 0 else lineBreaks!![index - 1]
        return Offset(measure(rowStart, offset), (firstRows[line] + index) * lineHeight)
    }

    /**
     * @param row a row of the new layout
     * @param create finds the parts of the row
     * @return the parts of the row that move on their own
     */
    fun partsOf(row: Int, create: () -> List<RowPart>): List<RowPart> = rowParts.getOrPut(row, create)

    /**
     * @param start where the part starts in the text
     * @param left where the part starts in the row
     * @param right where the part ends in the row
     * @param appearing whether this is something new, instead of something that moves
     */
    class RowPart(val start: Int, val left: Float, val right: Float, val appearing: Boolean = false)

    /** @return whether the text at [offset] is shown differently than before */
    fun isChanged(offset: Int) = changedFolds.any { offset >= it.min && offset < it.max }

    /** @return the edges of what is shown differently than before, which are in the given part of the text */
    fun changeEdgesWithin(start: Int, end: Int): List<Int> =
        changedFolds.flatMap { listOf(it.min, it.max) }.filter { it in start until end }

    /** @return the positions in the [line] where the text starts to move differently */
    fun breaksOf(line: Int): IntArray? = inserted?.breaksOf(line) ?: breaks.getOrNull(line)
}

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
) {
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
    fun toBefore(offset: Int): Int = when {
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
    fun positionOf(offset: Int): Offset {
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
    fun breaksOf(line: Int): IntArray? {
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
