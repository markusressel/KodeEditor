package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextRange
import kotlin.math.abs

/**
 * Decides whether the editor scrolls to the cursor. A user who panned or zoomed away keeps that view when
 * the editor changes its size, until the cursor moves or the editor gets the focus again.
 */
internal class CursorFollower {

    private var lastSelection: TextRange? = null
    private var wasFocused = false
    private var userMovedView = false

    fun onUserMovedView() {
        userMovedView = true
    }

    fun shouldFollow(selection: TextRange, focused: Boolean): Boolean {
        if (selection != lastSelection || (focused && !wasFocused)) userMovedView = false
        lastSelection = selection
        wasFocused = focused
        return focused && !userMovedView
    }
}

/**
 * Computes the scroll offset that keeps the cursor of the editor visible
 */
internal object CursorVisibility {

    /**
     * Where the cursor sits after scrolling to it vertically, as a fraction of the visible height
     * from the top. The upper third leaves room for the lines that are typed next.
     */
    const val TARGET_FRACTION = 1f / 3f

    /**
     * How far from the vertical center the middle of a range can be without moving the view, as a fraction of the
     * visible height
     */
    const val CENTER_TOLERANCE = 0.1f

    /**
     * The offset that has the middle of a range in the vertical center of the [viewport], or [offset] if it is near.
     *
     * @param offset the current scroll offset
     * @param top the top of the range in text coordinates
     * @param bottom the bottom of the range in text coordinates
     * @param viewport the size of the visible area in text coordinates
     * @param maxOffset the largest allowed offset
     */
    fun centeredOffsetFor(offset: Offset, top: Float, bottom: Float, viewport: Size, maxOffset: Offset): Offset {
        if (viewport.height <= 0f) return offset
        val wanted = (top + bottom - viewport.height) / 2
        if (abs(wanted - offset.y) <= viewport.height * CENTER_TOLERANCE) return offset
        return offset.copy(y = wanted.coerceIn(0f, maxOffset.y.coerceAtLeast(0f)))
    }

    /**
     * The offset that shows [cursor] in the [viewport], or [offset] when it is visible already.
     *
     * A cursor that left the visible area (or comes closer than one line to its top or bottom edge) is
     * moved to the upper third of the visible area, horizontally to the middle.
     *
     * @param offset the current scroll offset (top left corner of the visible area in text coordinates)
     * @param cursor the cursor rectangle in text coordinates
     * @param viewport the size of the visible area in text coordinates
     * @param maxOffset the largest allowed offset
     */
    fun offsetFor(offset: Offset, cursor: Rect, viewport: Size, maxOffset: Offset): Offset {
        if (viewport.width <= 0f || viewport.height <= 0f) return offset
        val lineHeight = cursor.height.coerceAtLeast(1f)

        // keep one line of context above and below, unless the visible area is too small for that
        val verticalMargin = if (viewport.height >= 3 * lineHeight) lineHeight else 0f
        val y = if (cursor.top < offset.y + verticalMargin || cursor.bottom > offset.y + viewport.height - verticalMargin) {
            if (viewport.height >= 3 * lineHeight) {
                cursor.top - viewport.height * TARGET_FRACTION
            } else {
                cursor.top - (viewport.height - lineHeight) / 2
            }
        } else {
            offset.y
        }

        val horizontalMargin = (viewport.width / 10f).coerceAtMost(lineHeight * 2)
        val x = if (cursor.left < offset.x + horizontalMargin || cursor.right > offset.x + viewport.width - horizontalMargin) {
            cursor.left - viewport.width / 2
        } else {
            offset.x
        }

        return Offset(
            x = x.coerceIn(0f, maxOffset.x.coerceAtLeast(0f)),
            y = y.coerceIn(0f, maxOffset.y.coerceAtLeast(0f)),
        )
    }
}
