package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

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
