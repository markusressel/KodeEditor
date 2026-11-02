package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorGeometryTest {

    private val content = IntSize(2000, 5000)

    private fun geometry(zoom: Float) = EditorGeometry(IntSize(1000, 1000), gutterWidth = 100, textStart = 8f, zoom = zoom)

    @Test
    fun `at zoom 1 the text area is visible`() {
        val geometry = geometry(zoom = 1f)
        assertEquals(Size(900f, 1000f), geometry.visibleSize)
        assertEquals(Offset(1100f, 4000f), geometry.maxOffset(content))
    }

    @Test
    fun `zooming in shows less and allows scrolling further`() {
        val geometry = geometry(zoom = 2f)
        // the bar stops at 150 of the 1000 pixels on screen
        assertEquals(Size(425f, 500f), geometry.visibleSize)
        assertEquals(Offset(1575f, 4500f), geometry.maxOffset(content))
    }

    @Test
    fun `zooming out shows more and allows scrolling less`() {
        val geometry = geometry(zoom = 0.5f)
        assertEquals(Size(1900f, 2000f), geometry.visibleSize)
        assertEquals(Offset(100f, 3000f), geometry.maxOffset(content))
    }

    @Test
    fun `a document smaller than the visible area does not scroll`() {
        val geometry = geometry(zoom = 0.25f)
        assertEquals(Offset.Zero, geometry.maxOffset(IntSize(100, 100)))
    }

    @Test
    fun `the bar shrinks with the text and stops growing with it`() {
        assertEquals(25f, geometry(zoom = 0.25f).gutterOnScreen)
        assertEquals(100f, geometry(zoom = 1f).gutterOnScreen)
        assertEquals(150f, geometry(zoom = 1.5f).gutterOnScreen)
        assertEquals(150f, geometry(zoom = 10f).gutterOnScreen)
    }

    @Test
    fun `a bar wider than the editor leaves nothing visible`() {
        val geometry = EditorGeometry(IntSize(100, 1000), gutterWidth = 100, textStart = 0f, zoom = 2f)
        assertEquals(0f, geometry.visibleSize.width)
    }

    @Test
    fun `text and editor coordinates convert both ways at any zoom`() {
        val offset = Offset(50f, 300f)
        val point = Offset(120f, 700f)
        for (zoom in listOf(0.25f, 1f, 2.5f)) {
            val geometry = geometry(zoom)
            val editor = geometry.toEditor(point, offset)
            assertEquals(geometry.gutterOnScreen + (8 + 120 - 50) * zoom, editor.x, 0.01f)
            assertEquals((700 - 300) * zoom, editor.y, 0.01f)
            val back = geometry.toText(editor, offset)
            assertEquals(point.x, back.x, 0.01f)
            assertEquals(point.y, back.y, 0.01f)
        }
    }

    @Test
    fun `the point under the fingers stays there when the bar changes its width`() {
        val offset = Offset(100f, 200f)
        val centroid = Offset(300f, 400f)
        val newOffset = zoomedOffset(offset, 1f, 0.5f, centroid, Offset.Zero, oldStart = 100f, newStart = 50f)
        assertEquals(Offset(100f, 200f) + Offset(200f, 400f), newOffset + Offset(250f, 400f) / 0.5f)
    }

    @Test
    fun `an offset is kept within the bounds`() {
        val max = Offset(100f, 200f)
        assertEquals(Offset(50f, 60f), EditorGeometry.coerce(Offset(50f, 60f), max))
        assertEquals(Offset(100f, 0f), EditorGeometry.coerce(Offset(500f, -60f), max))
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
