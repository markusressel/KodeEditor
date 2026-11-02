package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawWindowTest {

    private val visible = Size(1000f, 2000f)

    @Test
    fun `the window contains the visible area at any offset`() {
        for (y in 0..50_000 step 317) {
            for (x in 0..5_000 step 271) {
                val window = DrawWindow.around(Offset(x.toFloat(), y.toFloat()), visible)
                assertTrue(window.left <= x && window.right >= x + visible.width)
                assertTrue(window.top <= y && window.bottom >= y + visible.height)
            }
        }
    }

    @Test
    fun `the window stays the same during a small pan`() {
        val first = DrawWindow.around(Offset(0f, 100f), visible)
        assertEquals(first, DrawWindow.around(Offset(30f, 1500f), visible))
    }

    @Test
    fun `the window stays the same during a small zoom`() {
        val first = DrawWindow.around(Offset(0f, 100f), visible)
        assertEquals(first, DrawWindow.around(Offset(0f, 100f), visible * 0.9f))
    }

    @Test
    fun `a negative offset is treated as the start`() {
        assertEquals(0, DrawWindow.around(Offset(-4f, 0f), visible).left)
    }

    @Test
    fun `the window fits in constraints at the lowest zoom`() {
        val window = DrawWindow.around(Offset.Zero, Size(10_000f, 16_000f))
        assertTrue(window.width <= 32767 && window.height <= 32767)
    }
}
