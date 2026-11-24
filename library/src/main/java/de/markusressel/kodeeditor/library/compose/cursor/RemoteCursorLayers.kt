package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.layout.placedAt
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

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
    val oneRow = layout.rowAt(one.y + height / 2)
    val otherRow = layout.rowAt(other.y + height / 2)
    val ordered = oneRow < otherRow || (oneRow == otherRow && one.x <= other.x)
    val (first, last) = if (ordered) one to other else other to one
    val (top, bottom) = if (ordered) oneRow to otherRow else otherRow to oneRow
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
 * The labels of other users at their first cursor, above it, or below it on the first row and at the start of a row
 * that continues a wrapped line, where the label would cover the text just written in the row above. A label stays
 * inside the right edge of the visible text while its cursor is visible. Only placing them follows a glide or a
 * scroll, and only drawing them a fade out, they are not composed again for it.
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
                            // above the cursor, except where that would cover what was just written in the row above
                            val below = rect.top < placeable.height || glides.atWrappedRowStart(current.value)
                            val y = if (below) rect.bottom else rect.top - placeable.height
                            val right = visibleRight()
                            // the cursor stays under the label, a label of a cursor out of sight stays with it
                            val x = if (rect.left > right) {
                                rect.left
                            } else {
                                rect.left
                                    .coerceAtMost(right - placeable.width)
                                    .coerceAtLeast(rect.left - placeable.width)
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
