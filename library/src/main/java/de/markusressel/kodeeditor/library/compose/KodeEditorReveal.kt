package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.floor
import kotlin.math.max

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
 * - A change of the user shows all hidden text at once, see [stop]. Changes of others keep moving the hidden text.
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
    private var carry = 0f

    private val listener = object : KodeEditorListener {
        override fun onLocalEdit(splice: TextSplice) = stop()
    }

    /** the hidden chunks in the order they are revealed */
    private val queue: List<AnnotatedString.Range<SpanStyle>>
        get() = state.rangeStyles
            .filter { it.tag.startsWith(prefix) }
            .sortedBy { it.tag.substring(prefix.length).toLong() }

    /** where the text is revealed at the moment, null when nothing is hidden */
    val head: Int? get() = queue.firstOrNull()?.start

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
        state.applyRemote(listOf(TextSplice(at, 0, text)))
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
        charsPerSecond = max(max(charsPerSecond, minCharsPerSecond), hiddenLength * 1000f / catchUpMs)
    }

    /** shows all hidden text at once, and ends its appearing */
    fun stop() {
        state.setRangeStyles(state.rangeStyles.filterNot { it.tag.startsWith(prefix) })
        state.finishAppearing(tag)
        rested()
    }

    /**
     * Reveals the text of [elapsedMs].
     *
     * @return whether text is still hidden
     */
    internal fun advance(elapsedMs: Long): Boolean {
        var pending = queue
        if (pending.isEmpty()) {
            rested()
            return false
        }
        carry += charsPerSecond * elapsedMs / 1000f
        var steps = floor(carry).toInt()
        carry -= steps
        val styles = state.rangeStyles.toMutableList()
        while (steps > 0 && pending.isNotEmpty()) {
            val range = pending.first()
            var start = range.start
            while (steps > 0 && start < range.end) {
                val collapsed = layout?.collapsedRangeAt(start)
                start = if (collapsed != null && collapsed.max > start) collapsed.max else start + 1
                steps--
            }
            start = start.coerceAtMost(range.end)
            val index = styles.indexOf(range)
            if (start < range.end) {
                styles[index] = AnnotatedString.Range(HIDDEN, start, range.end, range.tag)
            } else {
                styles.removeAt(index)
            }
            state.setRangeStyles(styles)
            state.appear(range.start, start, tag)
            if (start < range.end) break
            pending = pending.drop(1)
        }
        if (styles.none { it.tag.startsWith(prefix) }) rested()
        return queue.isNotEmpty()
    }

    /**
     * Reveals the hidden text frame by frame until it is cancelled, and shows it at once when a change of the user
     * comes or animations are off. [rememberKodeEditorReveal] runs this.
     */
    suspend fun keepRevealing() {
        state.addListener(listener)
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
        }
    }

    private fun rested() {
        charsPerSecond = 0f
        carry = 0f
    }

    private companion object {
        const val ORDER_SEPARATOR = '\u0000'
        const val FRAME_MS = 16L
        val HIDDEN = SpanStyle(color = Color.Transparent)
    }
}

/**
 * Remembers a [KodeEditorReveal] and reveals its text frame by frame while it is in the composition.
 *
 * @param state the text the chunks are inserted into
 * @param tag tells the text of this writer apart from the others, use one per writer
 */
@Composable
fun rememberKodeEditorReveal(state: KodeEditorState, tag: String = "reveal"): KodeEditorReveal {
    val reveal = remember(state, tag) { KodeEditorReveal(state, tag) }
    LaunchedEffect(reveal) { reveal.keepRevealing() }
    return reveal
}
