package de.markusressel.kodeeditor.library.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.util.lerp

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
 */
internal class ReflowTransition(
    private val state: KodeEditorState,
    private val firstRows: IntArray,
    private val breaks: Array<IntArray?>,
    private var previous: ReflowTransition?,
    private val lineHeight: Float,
    private val measure: (start: Int, end: Int) -> Float,
    private val changedFolds: List<TextRange> = emptyList(),
) {
    private val version = state.rawVersion
    private var startMs = -1L
    private var depth: Int = (previous?.depth ?: 0) + 1

    /** where text was before the change, which is the same on each frame */
    private val positions = HashMap<Int, Offset>()

    /** how rows are split, which is also the same on each frame */
    private val rowParts = HashMap<Int, List<RowPart>>()

    /** how far the text moved so far, from 0 to 1 */
    var progress = 0f
        private set

    /** how far the view scrolled because of the reflow, the text must not jump on the screen because of it */
    var scrolled = 0f

    /** whether the text is still the one this reflow started with */
    val valid: Boolean get() = version == state.rawVersion

    /** @param nowMs the time of the frame */
    fun onFrame(nowMs: Long) {
        previous?.let {
            it.onFrame(nowMs)
            if (it.progress >= 1f || depth > MAX_RUNNING_REFLOWS) previous = null
        }
        if (startMs < 0) startMs = nowMs
        val time = ((nowMs - startMs) / REFLOW_ANIMATION_MS.toFloat()).coerceAtMost(1f)
        progress = FastOutSlowInEasing.transform(time)
    }

    /** @return where the text at [offset] started this reflow, in the coordinates of the document */
    fun startOf(offset: Int): Offset {
        val position = positions.getOrPut(offset) { positionOf(offset) }
        val before = previous ?: return Offset(position.x, position.y + scrolled)
        val from = before.startOf(offset)
        return Offset(
            x = lerp(from.x, position.x, before.progress),
            y = lerp(from.y, position.y, before.progress) + scrolled,
        )
    }

    private fun positionOf(offset: Int): Offset {
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
    fun breaksOf(line: Int): IntArray? = breaks[line]
}
