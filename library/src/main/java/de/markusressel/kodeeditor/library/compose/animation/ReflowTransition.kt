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
 * @param state the text, which has to stay the same during the reflow, unless [before] keeps its places
 * @param before where the text was before the change
 * @param previous the reflow that was still running when this one started
 * @param changedFolds the parts of the text that are shown differently now, what follows them moves on its own
 * @param durationMs how long the text takes to its new place
 * @param easing how the text speeds up and slows down on its way
 * @param typedOn the insertion this one is right behind, like the key before of somebody who types, also when it is
 * done. Soon after it, see [TYPING_WINDOW_MS], the text moves at an even speed for the time between the two, so typing
 * does not pump
 */
internal class ReflowTransition(
    private val state: KodeEditorState,
    private val before: LayoutBefore,
    previous: ReflowTransition?,
    private val changedFolds: List<TextRange> = emptyList(),
    private var durationMs: Int = REFLOW_ANIMATION_MS,
    private var easing: Easing = FastOutSlowInEasing,
    private var typedOn: ReflowTransition? = null,
) {
    // the places of a transition from before a change of the text are about a text that is gone, unless it kept them.
    // Those are about the text right after its own change, so no other change may have come between, e.g. a key of
    // the user that does not slide
    private var previous: ReflowTransition? = previous?.takeIf {
        it.version == state.rawVersion ||
            (before.keepsPlaces && it.before.keepsPlaces && it.version == state.rawVersion - 1)
    }

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
            if (first && before.keepsPlaces && it.before.keepsPlaces) it.frozen = true
            if (it.progress >= 1f || depth > MAX_RUNNING_REFLOWS) previous = null
        }
        val keyBefore = typedOn
        // only its time is needed, the transitions do not keep each other
        typedOn = null
        var steady = false
        if (first && keyBefore != null && keyBefore.startMs >= 0 && durationMs > 0) {
            // the key before started a frame early too, so this is the time between the keys
            val since = nowMs - keyBefore.startMs - FRAME_MS
            if (since <= TYPING_WINDOW_MS) {
                keyIntervalMs = smoothedKeyInterval(keyBefore.keyIntervalMs, since.toFloat())
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
        val position = positions.getOrPut(offset) { before.positionOf(offset) }
        val running = previous ?: return Offset(position.x + scrolledX, position.y + scrolled)
        val from = running.startOf(before.toBefore(offset))
        return Offset(
            x = lerp(from.x, position.x, running.progress) + scrolledX,
            y = lerp(from.y, position.y, running.progress) + scrolled,
        )
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
    fun breaksOf(line: Int): IntArray? = before.breaksOf(line)
}

/** Where the text was before a change, which a [ReflowTransition] moves it from */
internal interface LayoutBefore {
    /** @return where the text at [offset] of the text now was, in the coordinates of the document */
    fun positionOf(offset: Int): Offset

    /** @return the positions where the [line] of the text now broke, relative to its start */
    fun breaksOf(line: Int): IntArray?

    /** @return the position in the text before the change */
    fun toBefore(offset: Int): Int = offset

    /**
     * whether it knows its places without the state, so the text can change while it moves, and the next change can
     * start from where the text is on its way
     */
    val keepsPlaces: Boolean get() = false
}

/**
 * Where the text was before the lines wrapped or folded differently, with the same text.
 *
 * @param state the text
 * @param firstRows the first row of each line before the change, and the number of rows at the end
 * @param breaks the positions where each line broke before the change
 * @param lineHeight the height of a row
 * @param measure the width of the text between two positions of the same row
 */
internal class LayoutBeforeReflow(
    private val state: KodeEditorState,
    private val firstRows: IntArray,
    private val breaks: Array<IntArray?>,
    private val lineHeight: Float,
    private val measure: (start: Int, end: Int) -> Float,
) : LayoutBefore {
    override fun positionOf(offset: Int): Offset {
        val line = state.lineOf(offset)
        val lineStart = state.lineStart(line)
        val lineBreaks = breaks[line]
        val index = lineBreaks?.count { it <= offset - lineStart } ?: 0
        val rowStart = lineStart + if (index == 0) 0 else lineBreaks!![index - 1]
        return Offset(measure(rowStart, offset), (firstRows[line] + index) * lineHeight)
    }

    override fun breaksOf(line: Int): IntArray? = breaks.getOrNull(line)
}
