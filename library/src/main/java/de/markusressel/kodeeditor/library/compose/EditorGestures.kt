package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType

/** what the pointer gestures on the text mean */
internal interface EditorGestureHandler {
    fun onDown(type: PointerType)
    fun onTap(position: Offset)
    fun onDoubleTap(position: Offset)

    /** a long press, or the press of a mouse, starts selecting */
    fun onSelectionStart(position: Offset, type: PointerType)
    fun onSelectionDrag(position: Offset)
    fun onSelectionEnd()
}

private sealed interface PressResult {
    data class Up(val position: Offset, val time: Long) : PressResult
    data object Canceled : PressResult
}

/**
 * Tells apart a tap, a double tap and a long press from the movement of the fingers, which the [ZoomLayout] below this
 * uses for panning and zooming. Only a long press takes the following movement away from it.
 *
 * Must be on a parent of the [ZoomLayout] and of the handles, whose consumed events end a gesture here.
 */
internal suspend fun PointerInputScope.detectEditorGestures(handler: EditorGestureHandler) {
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        handler.onDown(down.type)

        if (down.type == PointerType.Mouse) {
            down.consume()
            handler.onSelectionStart(down.position, down.type)
            followSelection(down, handler)
            return@awaitEachGesture
        }

        val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitRelease(down) }
        when (result) {
            null -> {
                handler.onSelectionStart(down.position, down.type)
                followSelection(down, handler)
            }

            PressResult.Canceled -> Unit

            is PressResult.Up -> {
                val distance = (result.position - lastTapPosition).getDistance()
                val isDouble = result.time - lastTapTime <= viewConfiguration.doubleTapTimeoutMillis &&
                    distance <= viewConfiguration.touchSlop * DOUBLE_TAP_SLOP_FACTOR
                if (isDouble) {
                    lastTapTime = 0L
                    handler.onDoubleTap(result.position)
                } else {
                    lastTapTime = result.time
                    lastTapPosition = result.position
                    handler.onTap(result.position)
                }
            }
        }
    }
}

private const val DOUBLE_TAP_SLOP_FACTOR = 4

private suspend fun AwaitPointerEventScope.awaitRelease(down: PointerInputChange): PressResult {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == down.id } ?: return PressResult.Canceled
        val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
        if (event.changes.size > 1 || change.isConsumed || moved) return PressResult.Canceled
        if (!change.pressed) return PressResult.Up(change.position, change.uptimeMillis)
    }
}

private suspend fun AwaitPointerEventScope.followSelection(down: PointerInputChange, handler: EditorGestureHandler) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        event.changes.forEach { it.consume() }
        if (!change.pressed) break
        handler.onSelectionDrag(change.position)
    }
    handler.onSelectionEnd()
}
