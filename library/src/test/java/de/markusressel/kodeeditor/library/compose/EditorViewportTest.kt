package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorViewportTest {

    private val content = IntSize(2000, 5000)

    @Test
    fun `at zoom 1 the text area is visible`() {
        val viewport = EditorViewport(IntSize(900, 1000), gutterWidth = 100, zoom = 1f)
        assertEquals(Size(900f, 1000f), viewport.visibleSize)
        assertEquals(Offset(1100f, 4000f), viewport.maxOffset(content))
    }

    @Test
    fun `zooming in shows less and allows scrolling further`() {
        val viewport = EditorViewport(IntSize(900, 1000), gutterWidth = 100, zoom = 2f)
        // the line numbers cover 200 of the 1000 pixels on screen, which are 100 in text coordinates
        assertEquals(Size(400f, 500f), viewport.visibleSize)
        assertEquals(Offset(1600f, 4500f), viewport.maxOffset(content))
    }

    @Test
    fun `zooming out shows more and allows scrolling less`() {
        val viewport = EditorViewport(IntSize(900, 1000), gutterWidth = 100, zoom = 0.5f)
        assertEquals(Size(1900f, 2000f), viewport.visibleSize)
        assertEquals(Offset(100f, 3000f), viewport.maxOffset(content))
    }

    @Test
    fun `a document smaller than the visible area does not scroll`() {
        val viewport = EditorViewport(IntSize(900, 1000), gutterWidth = 100, zoom = 0.25f)
        assertEquals(Offset.Zero, viewport.maxOffset(IntSize(100, 100)))
    }

    @Test
    fun `line numbers that cover the whole editor leave nothing visible`() {
        val viewport = EditorViewport(IntSize(900, 1000), gutterWidth = 100, zoom = 20f)
        assertEquals(0f, viewport.visibleSize.width)
    }

    @Test
    fun `an offset is kept within the bounds`() {
        val max = Offset(100f, 200f)
        assertEquals(Offset(50f, 60f), EditorViewport.coerce(Offset(50f, 60f), max))
        assertEquals(Offset(100f, 0f), EditorViewport.coerce(Offset(500f, -60f), max))
    }

    @Test
    fun `the point under the fingers stays there while zooming`() {
        val offset = Offset(100f, 200f)
        val centroid = Offset(300f, 400f)
        val newOffset = zoomedOffset(offset, oldZoom = 1f, newZoom = 2f, centroid = centroid, pan = Offset.Zero)
        assertEquals(offset + centroid, newOffset + centroid / 2f)
    }

    @Test
    fun `panning moves the content with the fingers at any zoom`() {
        val newOffset = zoomedOffset(Offset(100f, 200f), 2f, 2f, Offset(300f, 400f), pan = Offset(20f, -40f))
        assertEquals(Offset(90f, 220f), newOffset)
    }
}
