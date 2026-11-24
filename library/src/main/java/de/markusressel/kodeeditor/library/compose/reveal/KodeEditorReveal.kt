package de.markusressel.kodeeditor.library.compose.reveal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.animation.FRAME_MS
import de.markusressel.kodeeditor.library.compose.input.nextCharacter
import de.markusressel.kodeeditor.library.compose.layout.KodeEditorLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.SplicePhase
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.state.mapPosition
import kotlin.math.floor
import kotlin.math.max
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest

/**
 * Shows text that somebody else writes in chunks, like an agent, as if it was typed. Each chunk is in the text at
 * once, see [insert], and hidden by a transparent range style that keeps its space, so nothing below it moves. Then it
 * is revealed a few characters in each frame, each piece appearing with the appearance of the editor, if it has one.
 *
 * - Chunks wait in a queue, in the order they arrive. A new chunk never shows the text before it at once.
 * - The head, where the text is revealed, walks through the queue, see [head].
 * - The speed is [minCharsPerSecond], or faster, so all hidden text is shown within [catchUpMs] of its arrival. It
 *   rises when a chunk arrives and does not drop while text is hidden, so the reveal never stutters.
 * - A collapsed part, like a folded link, counts as a single character and is shown as a whole, see [layout].
 * - A change of the user shows all hidden text at once, see [stop]. So does a change of somebody else inside the hidden
 *   text or right at its start or end, as nobody types into hidden text. Changes elsewhere move it, like range styles.
 * - Other range styles stay as they are. An app that sets its own must keep the ones with this [tag] in
 *   [KodeEditorState.setRangeStyles], or their text shows at once.
 *
 * [keepRevealing] does the work, [rememberKodeEditorReveal] runs it. docs/reveal.md has the rules with all numbers.
 *
 * @param state the text the chunks are inserted into
 * @param tag tells the hidden and appearing text of this writer apart from the others, use one per writer
 * @param minCharsPerSecond how fast the text is revealed when there is little of it
 * @param catchUpMs how long text stays hidden at the most
 */
@Stable
class KodeEditorReveal(
    val state: KodeEditorState,
    val tag: String = "reveal",
    private val minCharsPerSecond: Float = 60f,
    private val catchUpMs: Long = 4000,
) {
    /** the layout of the editor, to show collapsed parts as a whole, pass it on from `KodeEditor(onTextLayout)` */
    var layout: KodeEditorLayout? = null

    private val prefix = "$tag$ORDER_SEPARATOR"
    private var nextOrder = 0L
    private var charsPerSecond = 0f

    /** the speed [charsPerSecond] goes to, see [SPEED_UP_MS] */
    private var targetCharsPerSecond = 0f
    private var carry = 0f
    private var inserting = false

    private val listener = object : KodeEditorListener {
        override fun onLocalEdit(splice: TextSplice) = stop()
    }

    // after the range styles moved with the change: what it inserted or where it removed text is at its start
    private val spliceListener: (TextSplice) -> Unit = { splice ->
        end = end?.let { mapPosition(it, splice) }
        val end = splice.start + splice.inserted.length
        val touched = state.rangeStyles.any { it.tag.startsWith(prefix) && it.start <= end && splice.start <= it.end }
        if (touched && !inserting) stop()
    }

    /** the hidden chunks in the order they are revealed */
    private val queue: List<AnnotatedString.Range<SpanStyle>>
        get() = state.rangeStyles
            .filter { it.tag.startsWith(prefix) }
            .sortedBy { it.tag.substring(prefix.length).toLong() }

    /** where the text is revealed at the moment, null when nothing is hidden */
    val head: Int? get() = queue.firstOrNull()?.start

    /**
     * where the text that was revealed last ends, moved with the text, null before the first one and after [stop]. The
     * cursor of the writer can stay there between their chunks while nothing is hidden, instead of going away
     */
    var end: Int? by mutableStateOf(null)
        private set

    /** whether text is hidden */
    val isRevealing: Boolean get() = state.rangeStyles.any { it.tag.startsWith(prefix) }

    /** how many characters are hidden */
    val hiddenLength: Int get() = state.rangeStyles.sumOf { if (it.tag.startsWith(prefix)) it.end - it.start else 0 }

    /**
     * Inserts a chunk, hidden, with [KodeEditorState.applyRemote]. A chunk right behind the newest one or inside hidden
     * text joins it, any other one waits for the ones before it.
     *
     * @param position where to insert, in UTF-16 characters of the text
     * @param text the chunk
     */
    fun insert(position: Int, text: String) {
        if (text.isEmpty()) return
        val at = position.coerceIn(0, state.text.length)
        val before = queue
        val inside = before.any { it.start < at && at < it.end }
        val newest = before.lastOrNull()
        inserting = true
        try {
            state.applyRemote(listOf(TextSplice(at, 0, text)))
        } finally {
            inserting = false
        }
        // in the same call as the change, so no frame shows the chunk
        val others = state.rangeStyles
        state.setRangeStyles(
            when {
                inside -> others
                newest != null && newest.end == at -> others.map {
                    if (it.tag == newest.tag) AnnotatedString.Range(HIDDEN, it.start, at + text.length, it.tag) else it
                }

                else -> others + AnnotatedString.Range(HIDDEN, at, at + text.length, "$prefix${nextOrder++}")
            },
        )
        // a bit faster than the hidden text needs, for the time it takes to get to that speed
        val needed = hiddenLength * 1000f / (catchUpMs - SPEED_UP_MS).coerceAtLeast(SPEED_UP_MS)
        targetCharsPerSecond = max(max(targetCharsPerSecond, minCharsPerSecond), needed)
        // from a rest it starts at once at its speed, while revealing it gets faster smoothly
        if (charsPerSecond == 0f) charsPerSecond = targetCharsPerSecond
    }

    /** shows all hidden text at once, and ends its appearing */
    fun stop() {
        state.setRangeStyles(state.rangeStyles.filterNot { it.tag.startsWith(prefix) })
        state.finishAppearing(tag)
        end = null
        rested()
    }

    /**
     * Reveals the text of [elapsedMs].
     *
     * @return whether text is still hidden
     */
    internal fun advance(elapsedMs: Long): Boolean {
        val pending = queue
        if (pending.isEmpty()) {
            rested()
            return false
        }
        if (charsPerSecond < targetCharsPerSecond) {
            val step = (targetCharsPerSecond - charsPerSecond) * (elapsedMs.toFloat() / SPEED_UP_MS).coerceAtMost(1f)
            charsPerSecond = (charsPerSecond + step).coerceAtLeast(charsPerSecond)
        }
        carry += charsPerSecond * elapsedMs / 1000f
        var steps = floor(carry).toInt()
        carry -= steps
        if (steps == 0) return true
        val text = state.rawText
        val styles = state.rangeStyles.toMutableList()
        val revealed = ArrayList<IntRange>()
        var left = pending.size
        for (range in pending) {
            if (steps <= 0) break
            var start = range.start
            while (steps > 0 && start < range.end) {
                val collapsed = layout?.collapsedRangeAt(start)
                start = if (collapsed != null && collapsed.max > start) collapsed.max else text.nextCharacter(start)
                steps--
            }
            start = start.coerceAtMost(range.end)
            val index = styles.indexOf(range)
            if (start < range.end) {
                styles[index] = AnnotatedString.Range(HIDDEN, start, range.end, range.tag)
            } else {
                styles.removeAt(index)
                left--
                end = range.end
            }
            revealed += range.start until start
        }
        // the styles once per frame, the pieces in the order of the queue
        state.setRangeStyles(styles)
        revealed.forEach { state.appear(it.first, it.last + 1, tag) }
        if (left == 0) rested()
        return left > 0
    }

    /**
     * Reveals the hidden text frame by frame until it is cancelled, with the frame clock of the coroutine, and shows it
     * at once when animations are off. Changes that show the hidden text at once are only noticed while this runs. It
     * can be cancelled and run again, the hidden text and the speed stay. [rememberKodeEditorReveal] runs this.
     */
    suspend fun keepRevealing() {
        state.addListener(listener)
        state.addSpliceObserver(SplicePhase.Listeners, spliceListener)
        try {
            snapshotFlow { isRevealing }.collectLatest { revealing ->
                if (!revealing) return@collectLatest
                var last = -1L
                while (true) {
                    val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                    if (scale == 0f) {
                        stop()
                        return@collectLatest
                    }
                    withFrameMillis { now ->
                        // the first frame reveals a frame of text too, so a chunk starts to show at once
                        advance(if (last < 0) FRAME_MS else now - last)
                        last = now
                    }
                }
            }
        } finally {
            state.removeListener(listener)
            state.removeSpliceObserver(SplicePhase.Listeners, spliceListener)
        }
    }

    /** @return the position behind the character at [index], a surrogate pair is one character */
    private fun String.nextCharacter(index: Int): Int =
        if (index + 1 < length && this[index].isHighSurrogate() && this[index + 1].isLowSurrogate()) index + 2 else index + 1

    private fun rested() {
        charsPerSecond = 0f
        targetCharsPerSecond = 0f
        carry = 0f
    }

    private companion object {
        const val ORDER_SEPARATOR = '\u0000'

        /**
         * how long the reveal takes to get faster, about, so it does not jump to a new speed when a chunk arrives. It
         * aims for a bit more than the hidden text needs, so it is still shown within [catchUpMs]
         */
        const val SPEED_UP_MS = 150L
        val HIDDEN = SpanStyle(color = Color.Transparent)
    }
}

/**
 * Remembers a [KodeEditorReveal] and reveals its text frame by frame while it is in the composition.
 *
 * @param state the text the chunks are inserted into
 * @param tag tells the text of this writer apart from the others, use one per writer
 * @param minCharsPerSecond how fast the text is revealed when there is little of it
 * @param catchUpMs how long text stays hidden at the most
 */
@Composable
fun rememberKodeEditorReveal(
    state: KodeEditorState,
    tag: String = "reveal",
    minCharsPerSecond: Float = 60f,
    catchUpMs: Long = 4000,
): KodeEditorReveal {
    val reveal = remember(state, tag, minCharsPerSecond, catchUpMs) {
        KodeEditorReveal(state, tag, minCharsPerSecond, catchUpMs)
    }
    LaunchedEffect(reveal) { reveal.keepRevealing() }
    return reveal
}
