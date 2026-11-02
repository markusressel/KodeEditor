package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/**
 * Where things are in the editor: the bar with the line numbers on the left, and the text in what is left of it.
 *
 * There are two kinds of coordinates. Those of the editor are pixels on the screen. Those of the text are pixels at
 * zoom 1, with the scroll offset as the position of the content in the top left corner of the text area.
 *
 * @param size the size of the editor
 * @param gutterWidth the width of the bar at zoom 1
 * @param textStart the padding in front of the text at zoom 1
 * @param zoom the zoom factor of the text
 */
internal data class EditorGeometry(
    val size: IntSize,
    val gutterWidth: Int,
    val textStart: Float,
    val zoom: Float,
) {
    /** The bar shrinks with the text so its content fits the rows, but it does not take away the room of the text */
    val gutterScale: Float get() = min(zoom, MAX_GUTTER_SCALE)

    /** the width of the bar on screen, where the text area starts */
    val gutterOnScreen: Float get() = gutterOnScreen(zoom)

    fun gutterOnScreen(zoom: Float) = gutterWidth * min(zoom, MAX_GUTTER_SCALE)

    /** the size of the text area in text coordinates */
    val visibleSize: Size
        get() = Size(
            width = ((size.width - gutterOnScreen) / zoom).coerceAtLeast(0f),
            height = size.height / zoom,
        )

    /**
     * @param content the size of the document in text coordinates
     * @return the offset at which the end of the document reaches the end of the visible area
     */
    fun maxOffset(content: IntSize): Offset {
        val visible = visibleSize
        return Offset(
            x = (content.width - visible.width).coerceAtLeast(0f),
            y = (content.height - visible.height).coerceAtLeast(0f),
        )
    }

    /** a point of the text on the screen, at the given scroll [offset] */
    fun toEditor(text: Offset, offset: Offset) = Offset(
        x = gutterOnScreen + (textStart + text.x - offset.x) * zoom,
        y = (text.y - offset.y) * zoom,
    )

    /** the point of the text at a point on the screen, at the given scroll [offset] */
    fun toText(editor: Offset, offset: Offset) = Offset(
        x = (editor.x - gutterOnScreen) / zoom + offset.x - textStart,
        y = editor.y / zoom + offset.y,
    )

    companion object {
        /** large enough to read, small enough to leave the room to the text at a high zoom */
        const val MAX_GUTTER_SCALE = 1.5f

        fun coerce(offset: Offset, maxOffset: Offset) = Offset(
            x = offset.x.coerceIn(0f, maxOffset.x),
            y = offset.y.coerceIn(0f, maxOffset.y),
        )
    }
}
