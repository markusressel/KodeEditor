package de.markusressel.kodeeditor.library.compose

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextRange
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the [KodeEditorViewState] needs from the editor it belongs to */
internal interface EditorViewport {
    /** where animations run, so they also work for a caller without a frame clock */
    val scope: CoroutineScope
    val offset: Offset
    val visibleSize: Size
    val animates: Boolean
    val visibleRange: TextRange?

    /** moves the view without the user, to the nearest offset that can be reached */
    fun moveTo(offset: Offset)

    /** @return the offset that shows the [position], the current one if it is visible already */
    fun offsetShowing(position: Int): Offset

    /** @return the offset that has the middle of the range in the center, the current one if it is near already */
    fun offsetCentering(start: Int, end: Int): Offset

    /** @return the offset that has the place [fraction] of the way down the [line] at the top */
    fun offsetAtTop(line: Int, fraction: Float): Offset

    fun isVisible(position: Int): Boolean
}

/**
 * The view of a [KodeEditor] on its text: scrolling to a place in the text without touching the cursor, the selection
 * or the focus, also in a read-only editor. This lets an app follow the cursor of another user, or open a text at a
 * position without opening the keyboard.
 *
 * A scroll ends early when a finger or a mouse goes down on the editor, when something else moves the view, and when a
 * newer scroll starts, which takes over the speed of a running one. While following, pass `keepCursorVisible = false`
 * to the editor, or changes of the text in front of the local cursor scroll to that cursor.
 */
@Stable
class KodeEditorViewState {
    internal var viewport by mutableStateOf<EditorViewport?>(null)

    /** the fingers of a pan or a zoom, or the fling after them, move the view */
    internal var gestureMoving by mutableStateOf(false)

    /** a dragged selection handle scrolls the view at the edge of the editor */
    internal var handleMoving by mutableStateOf(false)

    /** the thumb of a scrollbar is dragged */
    internal var scrollbarMoving by mutableStateOf(false)

    /** a jump is about to happen, so the editor shows nothing rather than the top of the text */
    internal var jumpPending by mutableStateOf(false)
        private set

    /** counts the scrolls and what ended them, a scroll that waits for the editor stops waiting when it changes */
    private var generation by mutableIntStateOf(0)
    private var running: Job? = null
    private var velocity = Offset.Zero

    /**
     * Whether the user moves the view: from the first movement of a gesture until the gesture and its fling have ended,
     * also while a finger holds the view still. A dragged selection handle counts while it scrolls the view, a
     * scrollbar while its thumb is held. A zoom with the keyboard is a single step, it only calls `onUserMovedView`.
     */
    val isUserMovingView: Boolean get() = gestureMoving || handleMoving || scrollbarMoving

    /**
     * The text from the start of the top visible row to the end of the bottom visible row, null before the first
     * layout. Scrolling a line without line wrapping sideways does not change it. Reading it is cheap, and what reads
     * it only runs again when another row reaches an edge of the editor.
     */
    val visibleRange: TextRange? get() = viewport?.visibleRange

    /**
     * Shows a position of the text. It stays where it is if the position is visible already, otherwise the position
     * moves to the upper third of the editor, and horizontally to the middle if it is outside. The zoom stays.
     *
     * Waits for the first layout if the editor has none yet. An editor that jumps then shows the text at this position
     * right away, instead of the top of the text first. Call this after the text is in the editor: a position behind
     * the end of the text means the end, also when more text arrives later.
     *
     * @param position the position in the text
     * @param animate whether the view moves there, instead of jumping. [KodeEditorAnimations.scrolling] can turn this
     * off
     * @return true if the view got there, false if something ended the scroll, at once while [isUserMovingView]
     */
    suspend fun scrollToPosition(position: Int, animate: Boolean = true): Boolean =
        scroll(animate) { it.offsetShowing(position) }

    /**
     * Moves the middle of a range of the text to the vertical center of the editor. The range can be larger than the
     * editor. It stays where it is if the middle is near the center already, so small differences do not move the view.
     * The zoom and the horizontal position stay. This waits and ends like [scrollToPosition].
     *
     * @param start one end of the range
     * @param end the other end of the range
     * @param animate whether the view moves there, instead of jumping. [KodeEditorAnimations.scrolling] can turn this
     * off
     * @return true if the view got there, false if something ended the scroll, at once while [isUserMovingView]
     */
    suspend fun scrollToRange(start: Int, end: Int, animate: Boolean = true): Boolean =
        scroll(animate) { it.offsetCentering(start, end) }

    /**
     * Puts a line exactly at the top of the editor, also when it is visible already or only a little away, so calling
     * this for every frame of another scrolling view follows it smoothly, for example a preview of the text. The zoom
     * and the horizontal position stay. Near the end of the text, the view only gets as far as it can. This waits and
     * ends like [scrollToPosition], and like all scrolls of this class it does not call `onUserMovedView`.
     *
     * @param line the line of the text, 0 for the first one. A line behind the end means the last one, a line in a fold
     * the place of the fold
     * @param fraction how far down the line the top of the editor is, from 0 for the top of its first row to 1 for the
     * bottom of its last row, which matters for a line that wraps into several rows
     * @param animate whether the view moves there, instead of jumping. [KodeEditorAnimations.scrolling] can turn this
     * off
     * @return true if the view got there, false if something ended the scroll, at once while [isUserMovingView]
     */
    suspend fun scrollToLine(line: Int, fraction: Float = 0f, animate: Boolean = false): Boolean =
        scroll(animate) { it.offsetAtTop(line, fraction) }

    /**
     * Reading this in a composition or a draw updates it when the view moves.
     *
     * @param position the position in the text
     * @return whether the whole row of the position is on the screen, false before the first layout. A position in a
     * fold is at the place of the fold.
     */
    fun isPositionVisible(position: Int): Boolean = viewport?.isVisible(position) ?: false

    /** ends a running scroll, because the user or the editor moves the view now */
    internal fun interrupt() {
        velocity = Offset.Zero
        jumpPending = false
        generation++
        running?.cancel()
    }

    private suspend fun scroll(animate: Boolean, target: (EditorViewport) -> Offset): Boolean {
        if (isUserMovingView) return false
        val keptVelocity = velocity
        interrupt()
        velocity = keptVelocity
        val mine = generation
        // hides the text before the editor could show the wrong part of it
        val jumpsOnOpen = !animate && viewport == null
        if (jumpsOnOpen) jumpPending = true
        try {
            snapshotFlow { viewport != null || generation != mine }.first { it }
            val current = viewport
            if (current == null || generation != mine) return false

            var arrived = false
            val job = current.scope.launch {
                if (animate && current.animates) {
                    arrived = animateTo(current, target(current))
                } else {
                    velocity = Offset.Zero
                    current.moveTo(target(current))
                    // the new offset reaches the screen with the next frame, which then also shows the text again
                    if (jumpsOnOpen) withFrameNanos { }
                    arrived = true
                }
            }
            running = job
            try {
                job.join()
            } catch (e: CancellationException) {
                job.cancel()
                throw e
            }
            return arrived
        } finally {
            if (generation == mine) jumpPending = false
        }
    }

    /** @return false if something else moved the view on the way */
    private suspend fun animateTo(viewport: EditorViewport, target: Offset): Boolean {
        if (target == viewport.offset && velocity == Offset.Zero) return true
        // a long way would only show text flying by and lay it all out
        val longest = viewport.visibleSize.height * LONGEST_ANIMATION_SCREENS
        val distance = target.y - viewport.offset.y
        if (abs(distance) > longest) {
            viewport.moveTo(viewport.offset.copy(y = target.y - distance.sign * longest))
            velocity = Offset.Zero
        }

        var last = viewport.offset
        var undisturbed = true
        AnimationState(Offset.VectorConverter, last, velocity).animateTo(
            targetValue = target,
            animationSpec = spring(stiffness = Spring.StiffnessLow, visibilityThreshold = Offset(0.5f, 0.5f)),
        ) {
            if (viewport.offset != last) {
                undisturbed = false
                cancelAnimation()
                return@animateTo
            }
            viewport.moveTo(value)
            last = viewport.offset
            this@KodeEditorViewState.velocity = velocity
        }
        velocity = Offset.Zero
        return undisturbed
    }

    private companion object {
        const val LONGEST_ANIMATION_SCREENS = 3f
    }
}

/**
 * @return a new [KodeEditorViewState], to pass to a [KodeEditor]
 */
@Composable
fun rememberKodeEditorViewState(): KodeEditorViewState = remember { KodeEditorViewState() }
