package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
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
 * appears, and the cursor of this editor never glides. The system setting that scales the duration of animations
 * scales this too, and turns it off with a scale of 0. The defaults are those of the MkDocsEditor web client.
 *
 * @param durationMs how long a cursor takes to its new place, it slows down towards the end (cubic ease-out)
 * @param sameRowWidthFraction how far a cursor may move within its row and still glide, as a part of the width of
 * the text area
 * @param nextRowLines how far sideways a cursor may move to the row above or below and still glide, in line heights
 */
@Immutable
data class KodeEditorCursorGlide(
    val durationMs: Int = 120,
    val sameRowWidthFraction: Float = 0.4f,
    val nextRowLines: Float = 3f,
)

/**
 * Where the cursors of other users are drawn, while they glide away from their place towards it. A glide is kept as
 * the distance from the place of the cursor, which shrinks to 0, so the layout can move the place meanwhile.
 */
internal class RemoteCursorGlides(private val layout: EditorLineLayout, private val textWidth: () -> Float) {

    private class Glide(var head: Int) {
        var startX = 0f
        var startY = 0f

        /** when the glide started, [NOT_STARTED] until the next frame, [NONE] while the cursor is at its place */
        var started = NONE
    }

    private val glides = HashMap<String, Glide>()

    /** the time of the current frame, reading it draws again with each frame of a glide */
    private var now by mutableLongStateOf(0L)

    /** whether a cursor glides, frames run only then */
    var isGliding by mutableStateOf(false)
        private set

    /** how cursors glide, null jumps */
    var config: KodeEditorCursorGlide? = null

    /** the system setting of the animation speed, 0 turns animations off */
    var durationScale = 1f

    /** how long a glide takes */
    private val durationMs: Long get() = ((config?.durationMs ?: 0) * durationScale).toLong()

    /** the text changed, a cursor that moves with it does not glide */
    val spliceListener: (TextSplice) -> Unit = { splice ->
        glides.values.forEach { it.head = mapPosition(it.head, splice) }
    }

    /** takes the cursors of the users, after each composition that may have changed them */
    fun update(cursors: List<KodeEditorRemoteCursor>) {
        val seen = HashSet<String>()
        val length = layout.text.length
        cursors.forEach { cursor ->
            cursor.selections.forEachIndexed { index, selection ->
                val key = keyOf(cursor, index)
                seen += key
                val head = selection.end.coerceIn(0, length)
                val glide = glides[key]
                if (glide == null) {
                    glides[key] = Glide(head)
                } else if (glide.head != head) {
                    moveTo(glide, head)
                }
            }
        }
        glides.keys.retainAll(seen)
    }

    private fun moveTo(glide: Glide, head: Int) {
        val config = config
        val shown = shownCaret(glide)
        glide.head = head
        val target = layout.shownCursorRect(head).topLeft
        if (config == null || durationMs <= 0 || !glides(config, target - shown)) {
            glide.started = NONE
            return
        }
        glide.startX = shown.x - target.x
        glide.startY = shown.y - target.y
        glide.started = NOT_STARTED
        isGliding = true
    }

    /** @return whether a move by [distance] glides, like in the web client */
    private fun glides(config: KodeEditorCursorGlide, distance: Offset): Boolean {
        val dx = abs(distance.x)
        val dy = abs(distance.y)
        val line = layout.lineHeight
        return when {
            dx < MIN_GLIDE && dy < MIN_GLIDE -> false
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
        isGliding = gliding
        return gliding
    }

    /** @return how far the cursor is drawn away from its place */
    private fun distanceOf(glide: Glide): Offset {
        val started = glide.started
        if (started == NONE) return Offset.Zero
        val progress = if (started == NOT_STARTED || durationMs <= 0) 0f else (now - started).toFloat() / durationMs
        val remaining = (1f - progress.coerceIn(0f, 1f)).let { it * it * it }
        return Offset(glide.startX * remaining, glide.startY * remaining)
    }

    private fun shownCaret(glide: Glide): Offset = layout.shownCursorRect(glide.head).topLeft + distanceOf(glide)

    /** @return where the cursor of a selection is drawn, its rect moved by the glide */
    fun caretRect(cursor: KodeEditorRemoteCursor, index: Int): Rect {
        val head = cursor.selections[index].end.coerceIn(0, layout.text.length)
        val rect = layout.shownCursorRect(head)
        val glide = glides[keyOf(cursor, index)] ?: return rect
        // reading the time here draws again for each frame
        now
        return rect.translate(distanceOf(glide))
    }

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
 * Draws the selections of other users in a layer of its own, below the text. The end of a selection at the cursor
 * follows the gliding cursor within its row.
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
                val all = cursors.value
                if (all.isEmpty()) return@drawBehind
                val area = window.value
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    all.forEach { cursor ->
                        cursor.selections.forEachIndexed { index, selection ->
                            if (!selection.collapsed) drawSelection(layout, cursor, index, selection, glides)
                        }
                    }
                }
            },
    )
}

private fun DrawScope.drawSelection(
    layout: EditorLineLayout,
    cursor: KodeEditorRemoteCursor,
    index: Int,
    selection: TextRange,
    glides: RemoteCursorGlides,
) {
    val length = layout.text.length
    val min = selection.min.coerceIn(0, length)
    val max = selection.max.coerceIn(0, length)
    if (min >= max) return
    val color = cursor.selectionColor
    val target = layout.shownCursorRect(selection.end.coerceIn(0, length))
    val caret = glides.caretRect(cursor, index)
    // a caret on its way between rows: the whole selection is drawn at its place
    if (caret == target || abs(caret.top - target.top) > 0.5f) {
        drawPath(layout.shownPathForRange(min, max), color)
        return
    }
    val row = layout.rowAt(target.center.y)
    if (selection.end >= selection.start) {
        // the end at the cursor, the rest of the row up to it follows the caret
        val rowStart = maxOf(min, layout.startOfRow(row))
        if (rowStart > min) drawPath(layout.shownPathForRange(min, rowStart), color)
        val left = layout.shownCursorRect(rowStart).left
        if (caret.left > left) drawRect(color, Offset(left, target.top), Size(caret.left - left, target.height))
    } else {
        val rowEnd = minOf(max, layout.endOfRow(row))
        if (max > rowEnd) drawPath(layout.shownPathForRange(rowEnd, max), color)
        val right = layout.shownCursorRect(rowEnd).left
        if (right > caret.left) drawRect(color, Offset(caret.left, target.top), Size(right - caret.left, target.height))
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
                val all = cursors.value
                if (all.isEmpty()) return@drawBehind
                val area = window.value
                val width = REMOTE_CARET_WIDTH.toPx()
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    all.forEach { cursor ->
                        cursor.selections.indices.forEach { index ->
                            val rect = glides.caretRect(cursor, index)
                            drawRect(
                                color = cursor.caretColor,
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
 * The labels of other users at their first cursor, above it, or below it on the first row. Only placing them follows
 * a glide, they are not composed again for it.
 */
@Composable
internal fun RemoteCursorLabels(
    cursors: List<KodeEditorRemoteCursor>,
    glides: RemoteCursorGlides,
    label: @Composable (KodeEditorRemoteCursor) -> Unit,
) {
    cursors.forEach { cursor ->
        if (cursor.selections.isEmpty()) return@forEach
        key(cursor.id) {
            // read while placing, a new place of the cursor only places the label again
            val current = rememberUpdatedState(cursor)
            Box(
                modifier = Modifier.layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    layout(placeable.width, placeable.height) {
                        val rect = glides.caretRect(current.value, 0)
                        val y = if (rect.top >= placeable.height) rect.top - placeable.height else rect.bottom
                        placeable.place(rect.left.roundToInt(), y.roundToInt())
                    }
                },
            ) {
                label(cursor)
            }
        }
    }
}

private val REMOTE_CARET_WIDTH = 2.dp
