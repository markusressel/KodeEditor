package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class CursorVisibilityTest {

    private val viewport = Size(400f, 600f)
    private val max = Offset(1000f, 5000f)
    private fun cursorAt(x: Float, y: Float) = Rect(x, y, x + 2f, y + 20f)

    @Test
    fun visibleCursorDoesNotScroll() {
        val offset = Offset(0f, 100f)
        assertEquals(offset, CursorVisibility.offsetFor(offset, cursorAt(50f, 300f), viewport, max))
    }

    @Test
    fun cursorBelowTheVisibleAreaMovesToTheUpperThird() {
        val result = CursorVisibility.offsetFor(Offset.Zero, cursorAt(50f, 900f), viewport, max)
        assertEquals(Offset(0f, 900f - 200f), result)
    }

    @Test
    fun cursorInTheLastLineIsTooCloseToTheEdge() {
        // the keyboard made the editor smaller, the cursor is now in the last visible line
        val result = CursorVisibility.offsetFor(Offset.Zero, cursorAt(50f, 590f), viewport, max)
        assertEquals(590f - 200f, result.y)
    }

    @Test
    fun cursorAboveTheVisibleAreaScrollsUp() {
        val result = CursorVisibility.offsetFor(Offset(0f, 1000f), cursorAt(50f, 400f), viewport, max)
        assertEquals(200f, result.y)
    }

    @Test
    fun offsetsAreClamped() {
        assertEquals(0f, CursorVisibility.offsetFor(Offset(0f, 500f), cursorAt(50f, 30f), viewport, max).y)
        assertEquals(100f, CursorVisibility.offsetFor(Offset.Zero, cursorAt(50f, 2000f), viewport, Offset(0f, 100f)).y)
    }

    @Test
    fun cursorRightOfTheVisibleAreaIsCenteredHorizontally() {
        val result = CursorVisibility.offsetFor(Offset.Zero, cursorAt(700f, 100f), viewport, max)
        assertEquals(Offset(500f, 0f), result)
    }

    @Test
    fun tinyViewportCentersTheLine() {
        val result = CursorVisibility.offsetFor(Offset.Zero, cursorAt(10f, 500f), Size(400f, 40f), max)
        assertEquals(490f, result.y)
    }
}
