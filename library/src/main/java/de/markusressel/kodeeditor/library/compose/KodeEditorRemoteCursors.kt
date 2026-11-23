package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The cursors and selections of another user, which a [KodeEditor] draws on top of its text.
 *
 * @param id tells the users apart, it keys the movement of the cursors and the label
 * @param selections the selections of the user, in UTF-16 characters of the text. The end of each one is where its
 * cursor is, a collapsed one is only a cursor. The label is at the cursor of the first one
 * @param caretColor the color of the cursors
 * @param selectionColor the color behind the selected text, which should not be opaque
 */
@Immutable
data class KodeEditorRemoteCursor(
    val id: String,
    val selections: List<TextRange>,
    val caretColor: Color,
    val selectionColor: Color,
)

/**
 * How the cursors of other users move to their new place, see [KodeEditorRemoteCursor]. A cursor glides when its user
 * moved it a little, also while it follows text that is revealed piece after piece: a new place during a glide glides
 * on from where the cursor is drawn. It jumps for all other moves, when the text in front of it changed, when only the
 * layout moves it, e.g. for a fold, a zoom or a new size, and for moves of less than half a pixel. A new cursor just
 * appears, and the cursor of this editor never glides. A selection glides with its cursor: its other end grows or
 * shrinks with it, however far. A user who leaves fades out, with the cursors, selections and label. The system setting
 * that scales the duration of animations scales this too, and turns it off with a scale of 0. The defaults are those of
 * the MkDocsEditor web client.
 *
 * @param durationMs how long a cursor takes to its new place, it slows down towards the end (cubic ease-out)
 * @param sameRowWidthFraction how far a cursor may move within its row and still glide, as a part of the width of
 * the text area
 * @param nextRowLines how far sideways a cursor may move to the row above or below and still glide, in line heights
 * @param fadeOutMs how long a user who leaves takes to fade out, 0 removes it at once
 */
@Immutable
data class KodeEditorCursorGlide(
    val durationMs: Int = 120,
    val sameRowWidthFraction: Float = 0.4f,
    val nextRowLines: Float = 3f,
    val fadeOutMs: Int = 200,
)

/**
 * Where the cursors of other users are drawn, while they glide away from their place towards it. A glide is kept as
 * the distance from the place of the cursor, which shrinks to 0, so the layout can move the place meanwhile.
 */
internal class RemoteCursorGlides(private val layout: EditorLineLayout, private val textWidth: () -> Float) {

    /** one end of a selection, the distance is where it starts to glide from, relative to its place */
    private class End(var offset: Int) {
        var startX = 0f
        var startY = 0f
    }

    /** a selection whose ends glide together, a cursor is a selection with both ends at one place */
    private class Glide(anchor: Int, head: Int) {
        val anchor = End(anchor)
        val head = End(head)

        /** when the glide started, [NOT_STARTED] until the next frame, [NONE] while the selection is at its place */
        var started = NONE
    }

    private val glides = HashMap<String, Glide>()

    /** a user who left, drawn at the last place of their cursors while fading out */
    class Leaving(var cursor: KodeEditorRemoteCursor) {
        /** when the fade started, [NOT_STARTED] until the next frame */
        var started = NOT_STARTED
    }

    /** the users in the last update, by id */
    private var present = emptyMap<String, KodeEditorRemoteCursor>()

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

    /** how cursors glide, null jumps */
    var config: KodeEditorCursorGlide? = null

    /** the system setting of the animation speed, 0 turns animations off */
    var durationScale = 1f

    /** how long a glide takes */
    private val durationMs: Long get() = ((config?.durationMs ?: 0) * durationScale).toLong()

    /** how long a user who left takes to fade out */
    private val fadeOutMs: Long get() = ((config?.fadeOutMs ?: 0) * durationScale).toLong()

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
                    moveTo(glide, anchor, head)
                }
            }
        }
        glides.keys.retainAll(seen)

        val now = cursors.associateBy { it.id }
        leaving.removeAll { it.cursor.id in now }
        if (fadeOutMs > 0) {
            present.values.filter { it.id !in now }.forEach { leaving += Leaving(it) }
            if (leaving.isNotEmpty()) isGliding = true
        }
        present = now
        hasCursors = cursors.isNotEmpty() || leaving.isNotEmpty()
    }

    /**
     * Both ends glide from where they are drawn now, when the cursor moves a little or stays, however far the other end
     * moves. A cursor that becomes a selection grows from it, a selection that becomes a cursor shrinks into it.
     */
    private fun moveTo(glide: Glide, anchor: Int, head: Int) {
        val config = config
        val shownAnchor = shown(glide, glide.anchor)
        val shownHead = shown(glide, glide.head)
        glide.anchor.offset = anchor
        glide.head.offset = head
        val anchorTarget = layout.shownCursorRect(anchor).topLeft
        val headTarget = layout.shownCursorRect(head).topLeft
        val anchorMove = anchorTarget - shownAnchor
        val headMove = headTarget - shownHead
        val moves = !anchorMove.isTiny() || !headMove.isTiny()
        // the rules are those of the caret, the other end grows or shrinks with it
        if (config == null || durationMs <= 0 || !moves || !fits(config, headMove)) {
            glide.started = NONE
            return
        }
        glide.anchor.startX = -anchorMove.x
        glide.anchor.startY = -anchorMove.y
        glide.head.startX = -headMove.x
        glide.head.startY = -headMove.y
        glide.started = NOT_STARTED
        isGliding = true
    }

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
            if (glide.started == NOT_STARTED) glide.started = timeMs
            if (glide.started == NONE) return@forEach
            if (timeMs - glide.started >= durationMs) glide.started = NONE else gliding = true
        }
        leaving.forEach { if (it.started == NOT_STARTED) it.started = timeMs }
        if (leaving.removeAll { timeMs - it.started >= fadeOutMs }) {
            hasCursors = present.isNotEmpty() || leaving.isNotEmpty()
        }
        if (leaving.isNotEmpty()) gliding = true
        isGliding = gliding
        return gliding
    }

    /** @return how far an end is drawn away from its place */
    private fun distanceOf(glide: Glide, end: End): Offset {
        val started = glide.started
        if (started == NONE) return Offset.Zero
        val progress = if (started == NOT_STARTED || durationMs <= 0) 0f else (now - started).toFloat() / durationMs
        val remaining = (1f - progress.coerceIn(0f, 1f)).let { it * it * it }
        return Offset(end.startX * remaining, end.startY * remaining)
    }

    private fun shown(glide: Glide, end: End): Offset =
        layout.shownCursorRect(end.offset).topLeft + distanceOf(glide, end)

    /** @return where the cursor of a selection is drawn, its rect moved by the glide */
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
        // reading the time here draws again for each frame
        val started = leaving.started
        val elapsed = if (started == NOT_STARTED) 0L else now - started
        return if (fadeOutMs <= 0) 0f else (1f - elapsed.toFloat() / fadeOutMs).coerceIn(0f, 1f)
    }

    /** @return how opaque a user is drawn, 1 unless they left */
    fun alphaOf(id: String): Float = leaving.firstOrNull { it.cursor.id == id }?.let { alphaOf(it) } ?: 1f

    /** @return the users to draw, with how opaque each one is */
    fun drawn(cursors: List<KodeEditorRemoteCursor>): List<Pair<KodeEditorRemoteCursor, Float>> =
        cursors.map { it to 1f } + leaving.map { it.cursor to alphaOf(it) }

    private fun keyOf(cursor: KodeEditorRemoteCursor, index: Int) = "$index/${cursor.id}"

    private companion object {
        const val NONE = -1L
        const val NOT_STARTED = -2L

        /** pixels, a smaller move jumps */
        const val MIN_GLIDE = 0.5f

        /** in line heights, a move to the row above or below is less than this */
        const val NEXT_ROW_HEIGHTS = 1.5f
    }
}

/**
 * Draws the selections of other users in a layer of its own, below the text. While a selection glides, its ends are
 * where they are drawn on their way, and the rows between them are covered like by a selection.
 */
@Composable
internal fun RemoteSelectionLayer(
    layout: EditorLineLayout,
    cursors: State<List<KodeEditorRemoteCursor>>,
    glides: RemoteCursorGlides,
    window: State<IntRect>,
) {
    Box(
        modifier = Modifier
            .placedAt { window.value }
            .graphicsLayer {}
            .drawBehind {
                val all = glides.drawn(cursors.value)
                if (all.isEmpty()) return@drawBehind
                val area = window.value
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    all.forEach { (cursor, alpha) ->
                        val color = cursor.selectionColor.let { it.copy(alpha = it.alpha * alpha) }
                        cursor.selections.forEachIndexed { index, selection ->
                            val ends = glides.glidingEnds(cursor, index)
                            if (ends != null) {
                                drawGlidingSelection(layout, ends.first, ends.second, color)
                            } else if (!selection.collapsed) {
                                val length = layout.text.length
                                val min = selection.min.coerceIn(0, length)
                                val max = selection.max.coerceIn(0, length)
                                if (min < max) drawPath(layout.shownPathForRange(min, max), color)
                            }
                        }
                    }
                }
            },
    )
}

/**
 * Draws a selection between two places on their way, given as the top left of their cursor rects: the rows of both
 * ends from and to these places, and the rows between them like a selection.
 */
private fun DrawScope.drawGlidingSelection(layout: EditorLineLayout, one: Offset, other: Offset, color: Color) {
    val height = layout.lineHeight
    if (abs(one.x - other.x) < 0.5f && abs(one.y - other.y) < 0.5f) return
    val firstRow = layout.rowAt(one.y + height / 2)
    val lastRow = layout.rowAt(other.y + height / 2)
    val ordered = firstRow < lastRow || (firstRow == lastRow && one.x <= other.x)
    val (first, last) = if (ordered) one to other else other to one
    val top = layout.rowAt(first.y + height / 2)
    val bottom = layout.rowAt(last.y + height / 2)
    if (top == bottom) {
        drawRect(color, Offset(first.x, first.y), Size(last.x - first.x, height))
        return
    }
    // an end on its way between two rows is drawn up to the row next to it, so no part is covered twice
    val topEnd = layout.shownCursorRect(layout.endOfRow(top)).left
    val topHeight = min(first.y + height, layout.shownCursorRect(layout.startOfRow(top + 1)).top) - first.y
    if (topEnd > first.x && topHeight > 0f) {
        drawRect(color, Offset(first.x, first.y), Size(topEnd - first.x, topHeight))
    }
    if (bottom - top > 1) {
        val start = layout.startOfRow(top + 1)
        val end = layout.endOfRow(bottom - 1)
        if (end > start) drawPath(layout.shownPathForRange(start, end), color)
    }
    val above = layout.shownCursorRect(layout.endOfRow(bottom - 1)).bottom
    val bottomTop = maxOf(last.y, first.y + height, above)
    val bottomHeight = last.y + height - bottomTop
    val bottomStart = layout.shownCursorRect(layout.startOfRow(bottom)).left
    if (last.x > bottomStart && bottomHeight > 0f) {
        drawRect(color, Offset(bottomStart, bottomTop), Size(last.x - bottomStart, bottomHeight))
    }
}

/** Draws the cursors of other users in a layer of its own, on top of the text */
@Composable
internal fun RemoteCaretLayer(
    cursors: State<List<KodeEditorRemoteCursor>>,
    glides: RemoteCursorGlides,
    window: State<IntRect>,
) {
    Box(
        modifier = Modifier
            .placedAt { window.value }
            .graphicsLayer {}
            .drawBehind {
                val all = glides.drawn(cursors.value)
                if (all.isEmpty()) return@drawBehind
                val area = window.value
                val width = REMOTE_CARET_WIDTH.toPx()
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    all.forEach { (cursor, alpha) ->
                        cursor.selections.indices.forEach { index ->
                            val rect = glides.caretRect(cursor, index)
                            drawRect(
                                color = cursor.caretColor.let { it.copy(alpha = it.alpha * alpha) },
                                topLeft = Offset(rect.center.x - width / 2, rect.top),
                                size = Size(width, rect.height),
                            )
                        }
                    }
                }
            },
    )
}

/**
 * The labels of other users at their first cursor, above it, or below it on the first row. A label stays inside the
 * right edge of the visible text while its cursor is visible. Only placing them follows a glide or a scroll, and only
 * drawing them a fade out, they are not composed again for it.
 */
@Composable
internal fun RemoteCursorLabels(
    cursors: List<KodeEditorRemoteCursor>,
    glides: RemoteCursorGlides,
    visibleRight: () -> Float,
    label: @Composable (KodeEditorRemoteCursor) -> Unit,
) {
    (cursors + glides.leaving.map { it.cursor }).forEach { cursor ->
        if (cursor.selections.isEmpty()) return@forEach
        key(cursor.id) {
            // read while placing, a new place of the cursor only places the label again
            val current = rememberUpdatedState(cursor)
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = glides.alphaOf(current.value.id) }
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints())
                        layout(placeable.width, placeable.height) {
                            val rect = glides.caretRect(current.value, 0)
                            val y = if (rect.top >= placeable.height) rect.top - placeable.height else rect.bottom
                            val right = visibleRight()
                            // the cursor stays under the label, a label of a cursor out of sight stays with it
                            val x = if (rect.left > right) {
                                rect.left
                            } else {
                                rect.left.coerceAtMost(right - placeable.width).coerceAtLeast(rect.left - placeable.width)
                            }
                            placeable.place(x.roundToInt(), y.roundToInt())
                        }
                    },
            ) {
                label(cursor)
            }
        }
    }
}

private val REMOTE_CARET_WIDTH = 2.dp
