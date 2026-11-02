package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

/**
 * The part of the document that the editor shows.
 *
 * The line numbers scale with the zoom and cover the start of the text layer, so the text is visible in
 * what is left of the editor after them.
 *
 * @param textArea the size of the editor without the line numbers, in pixels
 * @param gutterWidth the width of the line numbers, in pixels at zoom 1
 * @param zoom the zoom factor
 */
internal data class EditorViewport(val textArea: IntSize, val gutterWidth: Int, val zoom: Float) {

    /** the size of the visible area in text coordinates */
    val visibleSize: Size
        get() = Size(
            width = ((textArea.width + gutterWidth) / zoom - gutterWidth).coerceAtLeast(0f),
            height = textArea.height / zoom,
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

    companion object {
        fun coerce(offset: Offset, maxOffset: Offset) = Offset(
            x = offset.x.coerceIn(0f, maxOffset.x),
            y = offset.y.coerceIn(0f, maxOffset.y),
        )
    }
}
