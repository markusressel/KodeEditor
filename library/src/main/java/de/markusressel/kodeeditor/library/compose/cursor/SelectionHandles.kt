package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.input.EditorTouch
import de.markusressel.kodeeditor.library.compose.input.HandleKind
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState
import kotlin.math.roundToInt

private val HANDLE_TOUCH_SIZE: Dp = 40.dp
private val HANDLE_RADIUS: Dp = 11.dp
private val HANDLE_EDGE_SIZE = 48.dp
private val HANDLE_EDGE_SPEED = 12.dp

internal object HandleDrag {

    /**
     * @param target the point a handle is dragged to, in editor coordinates
     * @param area the size of the editor
     * @param textLeft where the text starts in the editor, after the line numbers
     * @param edgeSize the distance to an edge at which the view starts to scroll
     * @param speed the largest distance to scroll per frame, on screen
     * @return how far to scroll in this frame, on screen
     */
    fun edgeScroll(target: Offset, area: Size, textLeft: Float, edgeSize: Float, speed: Float) = Offset(
        x = axis(target.x, textLeft, area.width, edgeSize, speed),
        y = axis(target.y, 0f, area.height, edgeSize, speed),
    )

    /** the selection after the handle of this kind is dragged to the text position */
    fun selectionFor(kind: HandleKind, position: Int, current: TextRange) = when (kind) {
        HandleKind.Cursor -> TextRange(position)
        // the ends of a selection do not pass each other, there is always one character selected
        HandleKind.SelectionStart -> TextRange(position.coerceAtMost(current.max - 1).coerceAtLeast(0), current.max)
        HandleKind.SelectionEnd -> TextRange(current.min, position.coerceAtLeast(current.min + 1))
    }

    private fun axis(value: Float, start: Float, end: Float, edgeSize: Float, speed: Float): Float = when {
        value < start + edgeSize -> -speed * ((start + edgeSize - value) / edgeSize).coerceAtMost(1f)
        value > end - edgeSize -> speed * ((value - end + edgeSize) / edgeSize).coerceAtMost(1f)
        else -> 0f
    }
}

/**
 * A handle to drag the cursor or an end of the selection.
 *
 * A part of the editor, not a popup. It is placed without recomposing, and stays composed while its position is
 * outside the visible area.
 *
 * @param kind which handle this is
 * @param color the color of the handle
 * @param anchor the point the handle points at in editor coordinates, null to hide it
 * @param editorCoordinates the coordinates of the editor
 * @param onDrag called with the point the handle should point at now
 * @param onDragEnd called when the handle is released after a drag
 * @param onTap called when the handle is tapped
 */
@Composable
internal fun SelectionHandle(
    kind: HandleKind,
    color: Color,
    anchor: () -> Offset?,
    editorCoordinates: () -> LayoutCoordinates?,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
) {
    var ownCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentAnchor by rememberUpdatedState(anchor)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnTap by rememberUpdatedState(onTap)
    val touchSlop = LocalViewConfiguration.current.touchSlop

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val size = HANDLE_TOUCH_SIZE.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(size, size))
                layout(constraints.minWidth, constraints.minHeight) {
                    val point = anchor()
                    if (point == null) {
                        placeable.place(-2 * size, -2 * size)
                    } else {
                        placeable.place(point.x.roundToInt() - size / 2, point.y.roundToInt())
                    }
                }
            }
            .testTag(kind.tag)
            .onPlaced { ownCoordinates = it }
            .pointerInput(kind) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val start = editorPosition(ownCoordinates, editorCoordinates(), down) ?: return@awaitEachGesture
                    val startAnchor = currentAnchor() ?: return@awaitEachGesture
                    var dragged = false
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        val position = editorPosition(ownCoordinates, editorCoordinates(), change) ?: continue
                        val moved = position - start
                        if (dragged || moved.getDistance() > touchSlop) {
                            dragged = true
                            currentOnDrag(startAnchor + moved)
                        }
                    }
                    if (dragged) currentOnDragEnd() else currentOnTap()
                }
            }
            .drawBehind { drawHandle(kind, color) },
    )
}

/** The handle itself moves while it is dragged, so the position must not be taken relative to it */
private fun editorPosition(
    own: LayoutCoordinates?,
    editor: LayoutCoordinates?,
    change: PointerInputChange,
): Offset? {
    if (own == null || editor == null || !own.isAttached || !editor.isAttached) return null
    return editor.localPositionOf(own, change.position)
}

/** A circle with one corner, which points at the top center of the touch area */
private fun DrawScope.drawHandle(kind: HandleKind, color: Color) {
    val radius = HANDLE_RADIUS.toPx()
    val tip = Offset(size.width / 2, 0f)
    when (kind) {
        HandleKind.Cursor -> {
            val center = tip + Offset(0f, radius * 1.41f)
            rotate45(center) {
                drawRect(color, topLeft = center - Offset(radius, radius), size = Size(radius, radius))
            }
            drawCircle(color, radius, center)
        }

        HandleKind.SelectionStart -> {
            val center = tip + Offset(-radius, radius)
            drawRect(color, topLeft = center - Offset(0f, radius), size = Size(radius, radius))
            drawCircle(color, radius, center)
        }

        HandleKind.SelectionEnd -> {
            val center = tip + Offset(radius, radius)
            drawRect(color, topLeft = center - Offset(radius, radius), size = Size(radius, radius))
            drawCircle(color, radius, center)
        }
    }
}

private fun DrawScope.rotate45(pivot: Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.rotate(45f, pivot)
    block()
    drawContext.transform.rotate(-45f, pivot)
}

/**
 * Near an edge of the editor a dragged handle scrolls the view, so it can reach the whole text.
 *
 * @param touch which handle is dragged where
 * @param viewState is told while the handle moves the view
 * @param area the size of the editor
 * @param textLeft where the text starts in the editor, after the bar
 * @param scrolled the offset of the view after scrolling by a distance on screen, null if it can not move
 * @param scrollTo moves the view to an offset
 * @param onUserMovedView called when the handle starts to move the view
 * @param moveHandle moves the handle of this kind to the point it is dragged to, after the view moved
 */
@Composable
internal fun HandleEdgeScrollEffect(
    touch: EditorTouch,
    viewState: KodeEditorViewState,
    area: () -> Size,
    textLeft: () -> Float,
    scrolled: (Offset) -> Offset?,
    scrollTo: (Offset) -> Unit,
    onUserMovedView: () -> Unit,
    moveHandle: (HandleKind, Offset) -> Unit,
) {
    val edgeSize = LocalDensity.current.run { HANDLE_EDGE_SIZE.toPx() }
    val edgeSpeed = LocalDensity.current.run { HANDLE_EDGE_SPEED.toPx() }
    LaunchedEffect(touch.draggedHandle) {
        val kind = touch.draggedHandle ?: return@LaunchedEffect
        try {
            while (true) {
                withFrameNanos { }
                val target = touch.dragTarget ?: continue
                val distance = HandleDrag.edgeScroll(target, area(), textLeft(), edgeSize, edgeSpeed)
                val moved = scrolled(distance) ?: continue
                if (!viewState.handleMoving) {
                    viewState.handleMoving = true
                    viewState.interrupt()
                    onUserMovedView()
                }
                scrollTo(moved)
                moveHandle(kind, target)
            }
        } finally {
            viewState.handleMoving = false
        }
    }
}
