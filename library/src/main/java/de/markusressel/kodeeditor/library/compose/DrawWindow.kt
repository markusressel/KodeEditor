package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The part of the document that is recorded for drawing, in text coordinates.
 *
 * Recording only what is visible would have to happen on every frame of a pan. So the window is larger than the
 * visible area, and only changes when the visible area leaves it. Its size is a power of two, so it also stays the
 * same during most frames of a zoom.
 */
internal object DrawWindow {

    /** the largest size per direction that can always be represented in constraints */
    private const val MAX_SIZE = 32767

    /**
     * @param offset the top left corner of the visible area
     * @param visible the size of the visible area
     * @return a rectangle that contains the visible area
     */
    fun around(offset: Offset, visible: Size): IntRect {
        val (left, right) = axis(offset.x, visible.width)
        val (top, bottom) = axis(offset.y, visible.height)
        return IntRect(left, top, right, bottom)
    }

    private fun axis(start: Float, length: Float): Pair<Int, Int> {
        val step = powerOfTwoAtLeast(ceil(length).toInt().coerceAtLeast(1))
        val from = (floor(start.coerceAtLeast(0f) / step).toInt()) * step
        return from to from + (2 * step).coerceAtMost(MAX_SIZE)
    }

    private fun powerOfTwoAtLeast(value: Int): Int {
        return Int.MAX_VALUE.coerceAtMost(Integer.highestOneBit((value - 1).coerceAtLeast(1)) shl 1)
    }
}
