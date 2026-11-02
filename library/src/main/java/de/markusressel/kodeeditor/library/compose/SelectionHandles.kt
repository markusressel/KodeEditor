package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal const val CURSOR_HANDLE_TAG = "kodeeditor-cursor-handle"
internal const val SELECTION_START_HANDLE_TAG = "kodeeditor-selection-start-handle"
internal const val SELECTION_END_HANDLE_TAG = "kodeeditor-selection-end-handle"

private val HANDLE_TOUCH_SIZE: Dp = 40.dp
private val HANDLE_RADIUS: Dp = 11.dp

internal enum class HandleKind(val tag: String) {
    Cursor(CURSOR_HANDLE_TAG),
    SelectionStart(SELECTION_START_HANDLE_TAG),
    SelectionEnd(SELECTION_END_HANDLE_TAG),
}

/**
 * Converts between text coordinates and the coordinates of the editor on screen.
 *
 * @param gutterWidth the width of the line numbers in pixels at zoom 1
 * @param textStart the padding in front of the text in pixels at zoom 1
 * @param offset the current scroll offset
 * @param zoom the current zoom factor
 */
internal data class EditorTransform(
    val gutterWidth: Int,
    val textStart: Float,
    val offset: Offset,
    val zoom: Float,
) {
    fun toEditor(text: Offset) = Offset(
        x = (gutterWidth + textStart + text.x - offset.x) * zoom,
        y = (text.y - offset.y) * zoom,
    )

    fun toText(editor: Offset) = Offset(
        x = editor.x / zoom + offset.x - gutterWidth - textStart,
        y = editor.y / zoom + offset.y,
    )
}

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

    private fun axis(value: Float, start: Float, end: Float, edgeSize: Float, speed: Float): Float = when {
        value < start + edgeSize -> -speed * ((start + edgeSize - value) / edgeSize).coerceAtMost(1f)
        value > end - edgeSize -> speed * ((value - end + edgeSize) / edgeSize).coerceAtMost(1f)
        else -> 0f
    }
}

/**
 * The actions of the text menu.
 */
internal class HandleMenu(
    private val state: KodeEditorState,
    private val readOnly: Boolean,
    private val clipboardText: () -> AnnotatedString?,
    private val setClipboardText: (AnnotatedString) -> Unit,
) {
    private val selectedText get() = state.rawText.substring(state.selection.min, state.selection.max)

    fun copy() {
        setClipboardText(AnnotatedString(selectedText))
        state.setSelection(TextRange(state.selection.max))
    }

    fun cut() {
        if (readOnly) return
        setClipboardText(AnnotatedString(selectedText))
        state.replaceSelection("")
    }

    fun paste() {
        if (readOnly) return
        state.replaceSelection(clipboardText()?.text ?: return)
    }

    fun selectAll() = state.selectAll()
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
