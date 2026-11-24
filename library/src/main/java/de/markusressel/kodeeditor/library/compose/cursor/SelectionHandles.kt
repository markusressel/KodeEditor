package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.EditorTouch
import de.markusressel.kodeeditor.library.compose.input.EditorGestureHandler
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.view.EditorGeometry
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState
import kotlin.math.roundToInt

internal const val CURSOR_HANDLE_TAG = "kodeeditor-cursor-handle"
internal const val SELECTION_START_HANDLE_TAG = "kodeeditor-selection-start-handle"
internal const val SELECTION_END_HANDLE_TAG = "kodeeditor-selection-end-handle"

/** the handles that can be dragged, with the test tags of their views */
internal enum class HandleKind(val tag: String) {
    Cursor(CURSOR_HANDLE_TAG),
    SelectionStart(SELECTION_START_HANDLE_TAG),
    SelectionEnd(SELECTION_END_HANDLE_TAG),
}

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
 * @param onUserMovesView called when the handle starts to move the view, stops what moves it on its own
 * @param moveHandle moves the handle of this kind to the point it is dragged to, after the view moved
 */
@Composable
private fun HandleEdgeScrollEffect(
    touch: EditorTouch,
    viewState: KodeEditorViewState,
    area: () -> Size,
    textLeft: () -> Float,
    scrolled: (Offset) -> Offset?,
    scrollTo: (Offset) -> Unit,
    onUserMovesView: () -> Unit,
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
                    onUserMovesView()
                }
                scrollTo(moved)
                moveHandle(kind, target)
            }
        } finally {
            viewState.handleMoving = false
        }
    }
}

/**
 * The handles of the cursor and the selection, for touch input. Dragging one moves its end of the selection, near an
 * edge of the editor it scrolls the view, and a tap on the cursor handle shows or hides the text menu.
 *
 * @param modifier modifiers of the box of the handles
 * @param state the text
 * @param layout the layout of the text
 * @param touch what the touch does, the handles are only there while the last input was a touch
 * @param gestures a drag after a long press extends the selection from the pressed word
 * @param viewState is told while a handle moves the view
 * @param shown whether the editor has the focus, the handles are hidden without it
 * @param editable whether the text can be changed, a cursor handle is only there then
 * @param color the color of the handles
 * @param geometry the room of the text
 * @param offset the top left corner of the view in the text
 * @param size the size of the editor
 * @param editorCoordinates the coordinates of the editor
 * @param maxOffset the largest offset, at the end of the text
 * @param scrollTo moves the view to an offset
 * @param onUserMovesView called when a handle starts to move the view
 */
@Composable
internal fun EditorSelectionHandles(
    modifier: Modifier = Modifier,
    state: KodeEditorState,
    layout: EditorLineLayout,
    touch: EditorTouch,
    gestures: EditorGestureHandler,
    viewState: KodeEditorViewState,
    shown: Boolean,
    editable: Boolean,
    color: Color,
    geometry: () -> EditorGeometry,
    offset: () -> Offset,
    size: () -> IntSize,
    editorCoordinates: () -> LayoutCoordinates?,
    maxOffset: () -> Offset,
    scrollTo: (Offset) -> Unit,
    onUserMovesView: () -> Unit,
) {
    /** the point the handle at this text position points at, null outside the visible text */
    fun handleAnchor(position: Int): Offset? {
        val shownGeometry = geometry()
        val totalSize = size()
        val cursor = layout.localCursorRect(position)
        val point = shownGeometry.toEditor(cursor.bottomCenter, offset())
        val visibleX = point.x >= shownGeometry.gutterOnScreen && point.x <= totalSize.width
        return point.takeIf { visibleX && it.y >= 0f && it.y <= totalSize.height }
    }

    fun moveHandle(kind: HandleKind, target: Offset) {
        if (touch.pressedWord != null) {
            gestures.onSelectionDrag(target)
            return
        }
        val point = geometry().toText(target, offset())
        val position = layout.getOffsetForPosition(Offset(point.x, point.y - layout.lineHeight / 2))
        state.setSelection(HandleDrag.selectionFor(kind, position, state.selection))
    }

    HandleEdgeScrollEffect(
        touch = touch,
        viewState = viewState,
        area = { size().let { Size(it.width.toFloat(), it.height.toFloat()) } },
        textLeft = { geometry().gutterOnScreen },
        scrolled = { distance ->
            val current = offset()
            EditorGeometry.coerce(current + distance / geometry().zoom, maxOffset()).takeIf { it != current }
        },
        scrollTo = scrollTo,
        onUserMovesView = onUserMovesView,
        moveHandle = ::moveHandle,
    )

    val handles = when {
        !shown || !touch.touchInput -> emptyList()
        !state.selection.collapsed -> listOf(HandleKind.SelectionStart, HandleKind.SelectionEnd)
        editable && state.text.isNotEmpty() -> listOf(HandleKind.Cursor)
        else -> emptyList()
    }
    Box(modifier = modifier) {
        handles.forEach { kind ->
            key(kind) {
                SelectionHandle(
                    kind = kind,
                    color = color,
                    anchor = {
                        handleAnchor(
                            if (kind == HandleKind.SelectionEnd) state.selection.max
                            else state.selection.min
                        )
                    },
                    editorCoordinates = editorCoordinates,
                    onDrag = {
                        touch.dragTarget = it
                        touch.draggedHandle = kind
                        moveHandle(kind, it)
                    },
                    onDragEnd = {
                        touch.draggedHandle = null
                        touch.dragTarget = null
                    },
                    onTap = {
                        if (kind == HandleKind.Cursor) touch.cursorMenuRequested = !touch.cursorMenuRequested
                    },
                )
            }
        }
    }
}
