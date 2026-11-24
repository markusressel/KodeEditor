package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.animation.FRAME_MS
import de.markusressel.kodeeditor.library.compose.animation.MIN_STEP_MS
import de.markusressel.kodeeditor.library.compose.animation.TYPING_MAX_CHARS
import de.markusressel.kodeeditor.library.compose.animation.TYPING_SPAN_FACTOR
import de.markusressel.kodeeditor.library.compose.animation.TYPING_WINDOW_MS
import de.markusressel.kodeeditor.library.compose.animation.crispnessOf
import de.markusressel.kodeeditor.library.compose.animation.evenStepMs
import de.markusressel.kodeeditor.library.compose.animation.smoothedKeyInterval
import de.markusressel.kodeeditor.library.compose.animation.steadyStepEase
import de.markusressel.kodeeditor.library.compose.animation.steadyStepMs
import de.markusressel.kodeeditor.library.compose.animation.steadyStepSpeed
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.state.mapPosition
import kotlin.math.abs

/**
 * Where the cursors of other users are drawn, while they glide away from their place towards it. A glide is kept as
 * the distance from the place of the cursor, which shrinks to 0, so the layout can move the place meanwhile.
 */
internal class RemoteCursorGlides(private val layout: EditorLineLayout, private val textWidth: () -> Float) {

    /** one end of a selection, the distance is where it starts to glide from, relative to its place */
    private class End(var offset: Int) {
        var startX = 0f
        var startY = 0f

        /** how fast it moves at the start, in pixels per millisecond, null for the cubic ease-out */
        var velocity: Offset? = null
    }

    /** a selection whose ends glide together, a cursor is a selection with both ends at one place */
    private class Glide(anchor: Int, head: Int) {
        val anchor = End(anchor)
        val head = End(head)

        /** when the glide started, [NOT_STARTED] until the next frame, [NONE] while the selection is at its place */
        var started = NONE

        /** how long this glide takes, shorter when the cursor gets new places quickly */
        var durationMs = 0L

        /** the time of the frame of the last new place, the frame it started gliding in when it was at rest */
        var movedAt = NONE

        /** the time of the frame the last glide ended in */
        var endedAt = NONE

        /** whether it glides at an even speed, see [TYPING_WINDOW_MS] */
        var linear = false

        /** whether its last move was small enough to glide at an even speed, decided at its first frame */
        var small = false

        /** the smoothed time between its steps at an even speed, see [TYPING_SPAN_FACTOR] */
        var stepIntervalMs = -1f

        /** how crisp its steps are, see [crispnessOf], 0 is an even glide */
        var crisp = 0f
    }

    private val glides = HashMap<String, Glide>()

    /** a user who left, drawn at the last place of their cursors while fading out */
    class Leaving(var cursor: KodeEditorRemoteCursor) {
        /** when the fade started, [NOT_STARTED] until the next frame */
        var started = NOT_STARTED
    }

    /** the users in the last update, by id */
    private var present = emptyMap<String, KodeEditorRemoteCursor>()

    /** a user who came, drawn more and more opaque from [from] */
    private class Arriving(val from: Float) {
        /** when the fade started, [NOT_STARTED] until the next frame */
        var started = NOT_STARTED
    }

    /** users who came and fade in, by id */
    private val arriving = HashMap<String, Arriving>()

    /** whether the cursors were updated before, those that are there when the editor shows do not fade in */
    private var updated = false

    /** users who left and fade out, a change composes the labels and layers again */
    val leaving = mutableStateListOf<Leaving>()

    /** whether there is a cursor to draw, also one that fades out */
    var hasCursors by mutableStateOf(false)
        private set

    /** the time of the current frame, reading it draws again with each frame of a glide */
    private var now by mutableLongStateOf(0L)

    /** whether a cursor glides or fades out, frames run only then */
    var isGliding by mutableStateOf(false)
        private set

    /**
     * changes with each glide or fade that starts. A glide can end in the frame another one starts in, then
     * [isGliding] is the same afterwards, while the frames have stopped
     */
    var starts by mutableIntStateOf(0)
        private set

    /** how cursors glide, null jumps */
    var config: KodeEditorCursorGlide? = null

    /** the system setting of the animation speed, 0 turns animations off. It can change at any time */
    var durationScale: () -> Float = { 1f }

    /** where the labels are and how visible, they move with the frames of the glides */
    val labels = RemoteLabelMotion(animates = { config != null && durationMs > 0 }, wake = {
        isGliding = true
        starts++
    })

    /** how long a glide takes */
    private val durationMs: Long get() = ((config?.durationMs ?: 0) * durationScale()).toLong()

    /** how long a user who comes takes to fade in */
    private val fadeInMs: Long get() = ((config?.fadeInMs ?: 0) * durationScale()).toLong()

    /** how long a user who left takes to fade out */
    private val fadeOutMs: Long get() = ((config?.fadeOutMs ?: 0) * durationScale()).toLong()

    /** the text changed, a cursor that moves with it does not glide */
    val spliceListener: (TextSplice) -> Unit = { splice ->
        glides.values.forEach {
            it.anchor.offset = mapPosition(it.anchor.offset, splice)
            it.head.offset = mapPosition(it.head.offset, splice)
        }
        leaving.forEach {
            val selections = it.cursor.selections.map { selection ->
                TextRange(mapPosition(selection.start, splice), mapPosition(selection.end, splice))
            }
            it.cursor = it.cursor.copy(selections = selections)
        }
    }

    /** takes the cursors of the users, after each composition that may have changed them */
    fun update(cursors: List<KodeEditorRemoteCursor>) {
        val seen = HashSet<String>()
        val length = layout.text.length
        cursors.forEach { cursor ->
            cursor.selections.forEachIndexed { index, selection ->
                val key = keyOf(cursor, index)
                seen += key
                val anchor = selection.start.coerceIn(0, length)
                val head = selection.end.coerceIn(0, length)
                val glide = glides[key]
                if (glide == null) {
                    glides[key] = Glide(anchor, head)
                } else if (glide.anchor.offset != anchor || glide.head.offset != head) {
                    val jumped = moveTo(glide, anchor, head)
                    if (index == 0) labels.moved(cursor.id, jumped)
                }
            }
        }
        glides.keys.retainAll(seen)

        val now = cursors.associateBy { it.id }
        // a user who comes fades in, one who comes back while fading out from where they are
        arriving.keys.retainAll(now.keys)
        if (fadeInMs > 0 && updated) {
            now.keys.filter { it !in present }.forEach { id ->
                val from = leaving.firstOrNull { it.cursor.id == id }?.let { alphaOf(it) } ?: 0f
                arriving[id] = Arriving(from)
            }
            if (arriving.isNotEmpty()) {
                isGliding = true
                starts++
            }
        }
        leaving.removeAll { it.cursor.id in now }
        if (fadeOutMs > 0) {
            present.values.filter { it.id !in now }.forEach { leaving += Leaving(it) }
            if (leaving.isNotEmpty()) {
                isGliding = true
                starts++
            }
        }
        present = now
        updated = true
        // after the leaving ones are known, their labels fade out as they are
        labels.retain(now.keys + leaving.map { it.cursor.id })
        hasCursors = cursors.isNotEmpty() || leaving.isNotEmpty()
    }

    /**
     * Both ends glide from where they are drawn now, when the cursor moves a little or stays, however far the other end
     * moves. A cursor that becomes a selection grows from it, a selection that becomes a cursor shrinks into it.
     *
     * @return whether it jumped to its new place instead of gliding there
     */
    private fun moveTo(glide: Glide, anchor: Int, head: Int): Boolean {
        val config = config
        val shownAnchor = shown(glide, glide.anchor)
        val shownHead = shown(glide, glide.head)
        val headStep = abs(head - glide.head.offset)
        glide.anchor.offset = anchor
        glide.head.offset = head
        val anchorTarget = layout.shownCursorRect(anchor).topLeft
        val headTarget = layout.shownCursorRect(head).topLeft
        val anchorMove = anchorTarget - shownAnchor
        val headMove = headTarget - shownHead
        val moves = !anchorMove.isTiny() || !headMove.isTiny()
        // a key or a backspace of the cursor: the text behind it slides, and carries the cursor with it, so both stay
        // together. A glide of its own would add a second move with a time of its own
        val carried = anchor == head && headStep <= TYPING_MAX_CHARS && layout.carriesCursorAt(head)
        // the rules are those of the caret, the other end grows or shrinks with it
        if (config == null || durationMs <= 0 || !moves || carried || !fits(config, headMove)) {
            glide.started = NONE
            glide.movedAt = NONE
            glide.linear = false
            return moves && !carried
        }
        // a step of a few characters in the row, like a key somebody types
        glide.small = anchor == head && headStep <= TYPING_MAX_CHARS && abs(headMove.y) < layout.lineHeight / 2
        // a cursor that gets a new place while it glides or shortly after, like at the head of text that is revealed,
        // catches up: its glide is a bit longer than the time between its places, so it stays close behind them. The
        // time of the frames stands still between glides, so a cursor at rest knows how long it rested only at its
        // next frame. Only a running glide counts, one that waits for its first frame does not know the time yet
        val gliding = glide.started >= 0
        // a glide that goes on keeps its speed, so there is no jerk at each new place
        var anchorVelocity = if (gliding) velocityOf(glide, glide.anchor) else null
        var headVelocity = if (gliding) velocityOf(glide, glide.head) else null
        glide.linear = false
        if (gliding && glide.movedAt != NONE) {
            val since = now - glide.movedAt
            if (glide.small && since <= TYPING_WINDOW_MS) {
                glide.linear = true
                glide.durationMs = steadyMs(glide, since)
                anchorVelocity = null
                headVelocity = null
            } else {
                glide.durationMs = catchUpMs(since)
                glide.stepIntervalMs = -1f
            }
            glide.movedAt = now
        } else {
            glide.durationMs = durationMs
        }
        glide.anchor.startX = -anchorMove.x
        glide.anchor.startY = -anchorMove.y
        glide.anchor.velocity = anchorVelocity?.let { keptGlideVelocity(it, anchorMove, glide.durationMs) }
        glide.head.startX = -headMove.x
        glide.head.startY = -headMove.y
        glide.head.velocity = headVelocity?.let { keptGlideVelocity(it, headMove, glide.durationMs) }
        // during a glide the frames run, so it goes on from the current one instead of losing a frame
        glide.started = if (gliding) now else NOT_STARTED
        isGliding = true
        starts++
        return false
    }

    /**
     * @return how long a glide at an even speed takes: a bit longer than the smoothed time between the steps, so it is
     * still on its way when the next one comes, and does not stop and start again at each step
     */
    private fun steadyMs(glide: Glide, since: Long): Long {
        glide.stepIntervalMs = smoothedKeyInterval(glide.stepIntervalMs, since.toFloat())
        val even = evenStepMs(glide.stepIntervalMs, minOf(MIN_STEP_MS, durationMs).toFloat()).toLong()
        // slow typing takes quick crisp steps instead, see CONTINUOUS_INTERVAL_MS
        glide.crisp = crispnessOf(glide.stepIntervalMs)
        return steadyStepMs(even.toFloat(), glide.crisp).toLong()
    }

    /** @return how long a glide takes that starts [since] the last new place of the cursor */
    private fun catchUpMs(since: Long): Long =
        (since * CATCH_UP_FACTOR).toLong().coerceIn(minOf(MIN_STEP_MS, durationMs), durationMs)

    private fun Offset.isTiny() = abs(x) < MIN_GLIDE && abs(y) < MIN_GLIDE

    /** @return whether a move by [distance] may glide, like in the web client. Staying is fine too */
    private fun fits(config: KodeEditorCursorGlide, distance: Offset): Boolean {
        val dx = abs(distance.x)
        val dy = abs(distance.y)
        val line = layout.lineHeight
        return when {
            distance.isTiny() -> true
            dy < line / 2 -> dx <= config.sameRowWidthFraction * textWidth()
            dy < line * NEXT_ROW_HEIGHTS -> dx <= config.nextRowLines * line
            else -> false
        }
    }

    /** @return whether a cursor still glides */
    fun onFrame(timeMs: Long): Boolean {
        now = timeMs
        var gliding = false
        glides.values.forEach { glide ->
            if (glide.started == NOT_STARTED) {
                // a glide that ended a frame ago ended in the frame this one got its place in, it goes on from there
                val ended = glide.endedAt
                val start = if (ended != NONE && timeMs - ended <= FRAME_SLACK_MS) ended else timeMs
                val since = start - glide.movedAt
                val steady = glide.small && glide.movedAt != NONE && since <= TYPING_WINDOW_MS
                val catchUp = !steady && glide.movedAt != NONE && since <= CATCH_UP_WINDOW_MS
                if (steady) {
                    glide.linear = true
                    glide.durationMs = steadyMs(glide, since)
                } else {
                    // a new run of steps starts with its own time between them
                    glide.stepIntervalMs = -1f
                    if (catchUp) glide.durationMs = catchUpMs(since)
                }
                // one that catches up moves in its first frame already
                glide.started = if ((catchUp || steady) && start == timeMs) timeMs - FRAME_MS else start
                glide.movedAt = glide.started
            }
            if (glide.started == NONE) return@forEach
            if (timeMs - glide.started >= glide.durationMs) {
                // the time of its last place stays, a new one shortly after still catches up
                glide.started = NONE
                glide.endedAt = timeMs
            } else {
                gliding = true
            }
        }
        arriving.values.forEach { if (it.started == NOT_STARTED) it.started = timeMs }
        arriving.values.removeAll { timeMs - it.started >= fadeInMs }
        if (arriving.isNotEmpty()) gliding = true
        leaving.forEach { if (it.started == NOT_STARTED) it.started = timeMs }
        if (leaving.removeAll { timeMs - it.started >= fadeOutMs }) {
            hasCursors = present.isNotEmpty() || leaving.isNotEmpty()
        }
        if (leaving.isNotEmpty()) gliding = true
        if (labels.onFrame(timeMs)) gliding = true
        isGliding = gliding
        return gliding
    }

    /** @return how far an end is drawn away from its place, see [glideDistance] */
    private fun distanceOf(glide: Glide, end: End): Offset {
        val started = glide.started
        if (started == NONE) return Offset.Zero
        val duration = glide.durationMs
        val t = if (started == NOT_STARTED || duration <= 0) 0f else ((now - started).toFloat() / duration).coerceIn(0f, 1f)
        val start = Offset(end.startX, end.startY)
        if (glide.linear) return start * (1f - steadyStepEase(t, glide.crisp))
        return glideDistance(start, end.velocity, t, duration)
    }

    /** @return how fast an end moves now, in pixels per millisecond */
    private fun velocityOf(glide: Glide, end: End): Offset {
        val started = glide.started
        val duration = glide.durationMs
        if (started < 0 || duration <= 0) return Offset.Zero
        val t = ((now - started).toFloat() / duration).coerceIn(0f, 1f)
        val start = Offset(end.startX, end.startY)
        if (glide.linear) return start * (-steadyStepSpeed(t, glide.crisp) / duration)
        return glideVelocity(start, end.velocity, t, duration)
    }

    private fun shown(glide: Glide, end: End): Offset =
        layout.shownCursorRect(end.offset).topLeft + distanceOf(glide, end)

    /**
     * @return the place in the text of each cursor of the users, and the top left of where its caret is drawn now.
     * Reading it draws again for each frame of a glide
     */
    fun drawnCarets(): List<Pair<Int, Offset>> {
        now
        return glides.values.filter { it.anchor.offset == it.head.offset }.map { glide ->
            glide.head.offset to shown(glide, glide.head)
        }
    }

    /** @return where the cursor of the selection at [index] of a user is drawn, its rect moved by the glide */
    fun caretRect(cursor: KodeEditorRemoteCursor, index: Int): Rect {
        val head = cursor.selections[index].end.coerceIn(0, layout.text.length)
        val rect = layout.shownCursorRect(head)
        val glide = glides[keyOf(cursor, index)] ?: return rect
        // reading the time here draws again for each frame
        now
        return rect.translate(distanceOf(glide, glide.head))
    }

    /**
     * @return where both ends of a selection are drawn while it glides, the top left of their cursor rects, null while
     * it is at its place
     */
    fun glidingEnds(cursor: KodeEditorRemoteCursor, index: Int): Pair<Offset, Offset>? {
        val glide = glides[keyOf(cursor, index)] ?: return null
        // reading the time here draws again for each frame
        now
        if (glide.started == NONE) return null
        return shown(glide, glide.anchor) to shown(glide, glide.head)
    }

    /** @return how opaque a user who left is drawn, it fades out linearly */
    fun alphaOf(leaving: Leaving): Float {
        // reading the time here draws again for each frame, also before the fade started
        val now = now
        val started = leaving.started
        val elapsed = if (started == NOT_STARTED) 0L else now - started
        return if (fadeOutMs <= 0) 0f else (1f - elapsed.toFloat() / fadeOutMs).coerceIn(0f, 1f)
    }

    /** @return how opaque a user is drawn, 1 unless they came just now or left */
    fun alphaOf(id: String): Float {
        leaving.firstOrNull { it.cursor.id == id }?.let { return alphaOf(it) }
        val arrived = arriving[id] ?: return 1f
        // reading the time here draws again for each frame, also before the fade started
        val now = now
        val started = arrived.started
        val elapsed = if (started == NOT_STARTED) 0L else now - started
        val t = if (fadeInMs <= 0) 1f else (elapsed.toFloat() / fadeInMs).coerceIn(0f, 1f)
        return arrived.from + (1f - arrived.from) * t
    }

    /** @return the users to draw, with how opaque each one is */
    fun drawn(cursors: List<KodeEditorRemoteCursor>): List<Pair<KodeEditorRemoteCursor, Float>> =
        cursors.map { it to alphaOf(it.id) } + leaving.map { it.cursor to alphaOf(it) }

    /**
     * @return whether the first cursor of a user is at the start of a row that continues a wrapped line, where the text
     * it just wrote ends in the row above
     */
    fun atWrappedRowStart(cursor: KodeEditorRemoteCursor): Boolean {
        val text = layout.text
        val head = cursor.selections.firstOrNull()?.end?.coerceIn(0, text.length) ?: return false
        if (head == 0 || text[head - 1] == '\n') return false
        val rect = layout.getCursorRect(head)
        return layout.startOfRow(layout.rowAt(rect.top + layout.lineHeight / 2)) == head
    }

    private fun keyOf(cursor: KodeEditorRemoteCursor, index: Int) = "$index/${cursor.id}"

    private companion object {
        const val NONE = -1L
        const val NOT_STARTED = -2L

        /** in line heights, a move to the row above or below is less than this */
        const val NEXT_ROW_HEIGHTS = 1.5f

        /** a glide that starts during another one takes this times the time since that one started */
        const val CATCH_UP_FACTOR = 1.5f

        /** a new place this soon after the last one catches up, also when the glide just ended */
        const val CATCH_UP_WINDOW_MS = 90L

        /** at most the time between two frames */
        const val FRAME_SLACK_MS = 20L
    }
}
