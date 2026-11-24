package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import de.markusressel.kodeeditor.library.compose.animation.CubicEaseOut

/**
 * Where the labels of other users are, above or below their cursor, and how visible they are, so they stay calm while
 * somebody writes fast. The calm label (R9) of docs/reveal.md, keep both in step.
 *
 * - A label goes below its cursor at once when it must, and back above only after its writer rested for [REST_MS] or
 *   jumped, so a writer who wraps rows quickly switches it at most once per run of writing.
 * - A change of the side moves the label in [SIDE_MS] with the cubic ease-out, from where it is drawn.
 * - After [STEADY_MS] of writing without a rest, the label fades down to [DIM_ALPHA]. A rest, a jump or a cursor that
 *   comes into view shows it again.
 * - A label that covers the cursor of the user fades out, a mouse over a label or its cursor shows it.
 * - The app can show or hide a label always, and show it fully for [SIGNAL_MS] with a signal.
 *
 * Without animations, a label is placed anew at each place of its cursor and always shown.
 *
 * @param animates whether cursors glide, otherwise nothing of this moves
 * @param wake asks for frames, [onFrame] is called with each of them until it returns false
 */
internal class RemoteLabelMotion(private val animates: () -> Boolean, private val wake: () -> Unit) {

    private class Label {
        /** whether the label is below its cursor, or goes there */
        var below = false

        /** whether it was placed before, the first place and a jump decide the side at once */
        var placed = false
        var jumped = false

        /** where the label was between above (0) and below (1) when the side changed */
        var sideFrom = 0f
        var sideStartedAt = NONE

        /** a new place of the cursor waits for the time of the next frame */
        var moved = false
        var movedAt = NONE

        /** when the current run of writing started, [NOT_STARTED] until the next frame */
        var runStartedAt = NONE

        /** whether the cursor was out of view, it starts a new run when it comes back */
        var unseen = false

        /** the last signal of the app, null before the first one */
        var signal: Int? = null

        /** until when a signal shows the label, [NOT_STARTED] until the next frame */
        var signaledUntil = NONE

        var alphaFrom = 1f
        var alphaTarget = 1f
        var alphaStartedAt = NONE
    }

    private val labels = HashMap<String, Label>()

    /** the time of the current frame, reading it places and draws the labels again with each frame */
    private var now by mutableLongStateOf(0L)

    /** where the mouse is over the text, null without one */
    var hover by mutableStateOf<Offset?>(null)

    /** keeps the labels of [ids] only */
    fun retain(ids: Set<String>) {
        labels.keys.retainAll(ids)
    }

    /**
     * the first cursor of a user got a new place
     *
     * @param jumped whether it jumped there instead of gliding
     */
    fun moved(id: String, jumped: Boolean) {
        val label = labels.getOrPut(id) { Label() }
        label.moved = true
        label.jumped = label.jumped || jumped
        if (animates()) wake()
    }

    /** @return whether a label still changes, frames are needed then */
    fun onFrame(timeMs: Long): Boolean {
        now = timeMs
        var busy = false
        labels.values.forEach { label ->
            if (label.moved) {
                label.moved = false
                val rested = label.movedAt == NONE || timeMs - label.movedAt >= REST_MS
                if (rested || label.jumped || label.runStartedAt == NONE) label.runStartedAt = timeMs
                label.movedAt = timeMs
            }
            if (label.runStartedAt == NOT_STARTED) label.runStartedAt = timeMs
            if (label.signaledUntil == NOT_STARTED) label.signaledUntil = timeMs + SIGNAL_MS
            // one frame after it ended, so the label is faded for it
            if (label.signaledUntil >= 0 && timeMs > label.signaledUntil + REST_FRAME_MS) label.signaledUntil = NONE
            if (label.sideStartedAt == NOT_STARTED) label.sideStartedAt = timeMs
            if (label.sideStartedAt >= 0 && timeMs - label.sideStartedAt >= SIDE_MS) label.sideStartedAt = NONE
            if (label.alphaStartedAt == NOT_STARTED) label.alphaStartedAt = timeMs
            if (label.alphaStartedAt >= 0 && timeMs - label.alphaStartedAt >= FADE_MS) {
                label.alphaStartedAt = NONE
                label.alphaFrom = label.alphaTarget
            }
            // one frame after the rest, so the label is placed and faded for it
            val writing = label.movedAt != NONE && timeMs - label.movedAt <= REST_MS + REST_FRAME_MS
            val moving = label.sideStartedAt != NONE || label.alphaStartedAt != NONE || label.signaledUntil != NONE
            if (writing || moving) busy = true
        }
        return busy
    }

    /**
     * @param id the user
     * @param caret where the cursor of the user is drawn
     * @param width how wide the label is
     * @param height how high the label is
     * @param x where the label starts
     * @param below whether the label must be below its cursor now, otherwise it is above
     * @param inView whether the cursor is in view
     * @param localCaret where the cursor of the user is drawn, null without one
     * @param visibility whether the app wants the label shown by the rules, always or never
     * @param signal the signal of the app, a new value shows the label for a moment
     * @return where the top of the label is
     */
    fun place(
        id: String,
        caret: Rect,
        width: Float,
        height: Float,
        x: Float,
        below: Boolean,
        inView: Boolean,
        localCaret: Rect?,
        visibility: KodeEditorLabelVisibility,
        signal: Int,
    ): Float {
        val label = labels.getOrPut(id) { Label() }
        val above = caret.top - height
        val signaled = label.signal != null && label.signal != signal
        label.signal = signal
        if (!animates()) {
            label.below = below
            label.placed = false
            label.alphaFrom = if (visibility == KodeEditorLabelVisibility.Hidden) 0f else 1f
            label.alphaTarget = label.alphaFrom
            label.alphaStartedAt = NONE
            return if (below) caret.bottom else above
        }
        if (signaled) {
            label.signaledUntil = NOT_STARTED
            wake()
        }
        // reading the time places the label again with each frame
        val now = now
        val rested = label.movedAt == NONE || now - label.movedAt >= REST_MS
        if (!label.placed || label.jumped) {
            label.placed = true
            label.jumped = false
            label.below = below
            label.sideStartedAt = NONE
        } else if (below != label.below && (below || rested)) {
            label.sideFrom = sideOf(label, now)
            label.below = below
            label.sideStartedAt = NOT_STARTED
            wake()
        }
        val y = lerp(above, caret.bottom, sideOf(label, now))

        if (!inView) {
            label.unseen = true
        } else if (label.unseen) {
            label.unseen = false
            label.runStartedAt = NOT_STARTED
            wake()
        }
        val box = Rect(x, y, x + width, y + height)
        val hovered = hover?.let { box.inflate(HOVER_SLACK).contains(it) || caret.inflate(HOVER_SLACK).contains(it) }
        val steady = !rested && label.runStartedAt >= 0 && now - label.runStartedAt >= STEADY_MS
        val shownBySignal = label.signaledUntil == NOT_STARTED || now <= label.signaledUntil
        val target = when {
            visibility == KodeEditorLabelVisibility.Hidden -> 0f
            visibility == KodeEditorLabelVisibility.Shown -> 1f
            hovered == true || shownBySignal -> 1f
            localCaret != null && box.overlaps(localCaret) -> 0f
            steady -> DIM_ALPHA
            else -> 1f
        }
        if (target != label.alphaTarget) {
            label.alphaFrom = alphaOf(label, now)
            label.alphaTarget = target
            label.alphaStartedAt = NOT_STARTED
            wake()
        }
        return y
    }

    /** @return how visible the label of a user is drawn now */
    fun alphaOf(id: String): Float {
        val label = labels[id] ?: return 1f
        return alphaOf(label, now)
    }

    /** @return where the label is between above (0) and below (1) */
    private fun sideOf(label: Label, now: Long): Float {
        val target = if (label.below) 1f else 0f
        return when (val started = label.sideStartedAt) {
            NONE -> target
            NOT_STARTED -> label.sideFrom
            else -> {
                val t = ((now - started) / SIDE_MS.toFloat()).coerceIn(0f, 1f)
                lerp(label.sideFrom, target, CubicEaseOut.transform(t))
            }
        }
    }

    private fun alphaOf(label: Label, now: Long): Float = when (val started = label.alphaStartedAt) {
        NONE -> label.alphaTarget
        NOT_STARTED -> label.alphaFrom
        else -> lerp(label.alphaFrom, label.alphaTarget, ((now - started) / FADE_MS.toFloat()).coerceIn(0f, 1f))
    }

    companion object {
        private const val NONE = -1L
        private const val NOT_STARTED = -2L

        /** a writer who got no new place for this long rests, their label goes back above and is shown */
        const val REST_MS = 500L

        /** a writer who writes without a rest for this long has their label faded down */
        const val STEADY_MS = 1000L

        /** how long a label takes to change its side */
        const val SIDE_MS = 120L

        /** how long a label takes to fade down, out or in */
        const val FADE_MS = 200L

        /** how long a signal of the app shows a label fully */
        const val SIGNAL_MS = 1500L

        /** how visible the label of a writer who writes steadily is */
        const val DIM_ALPHA = 0.35f

        /** one frame more, so the label is placed for the rest */
        private const val REST_FRAME_MS = 16L

        /** pixels around a label and its cursor where a mouse still shows it */
        private const val HOVER_SLACK = 8f
    }
}
