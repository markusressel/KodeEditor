package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** the label of Jane, placed in each frame like the layout of the editor does */
class RemoteLabelMotionTest {

    private val motion = RemoteLabelMotion(animates = { true }, wake = {})
    private var time = 0L
    private val caret = Rect(100f, 100f, 102f, 120f)

    var visibility = KodeEditorLabelVisibility.Auto
    var signal = 0
    var localCaret: Rect? = null

    /** lets [ms] pass, Jane gets a new place every [keyMs] if it is above 0 */
    private fun pass(ms: Long, keyMs: Long = 0) {
        var sinceKey = 0L
        repeat((ms / FRAME).toInt()) {
            time += FRAME
            sinceKey += FRAME
            if (keyMs > 0 && sinceKey >= keyMs) {
                sinceKey = 0
                motion.moved("jane", jumped = false)
            }
            motion.onFrame(time)
            place()
        }
    }

    private fun place(below: Boolean = false) = motion.place(
        id = "jane",
        caret = caret,
        width = 40f,
        height = 20f,
        x = 100f,
        below = below,
        inView = true,
        localCaret = localCaret,
        visibility = visibility,
        signal = signal,
    )

    private val alpha get() = motion.alphaOf("jane")

    @Test
    fun `the label of somebody who writes steadily fades down, and shows again after a rest`() {
        place()
        pass(500)
        val atRest = alpha
        pass(800, keyMs = 64)
        val early = alpha
        pass(700, keyMs = 64)
        val typing = alpha
        pass(800)

        assertEquals(1f, atRest)
        assertEquals("shown at the start of the run", 1f, early)
        assertEquals("faded down", RemoteLabelMotion.DIM_ALPHA, typing, 0.01f)
        assertEquals("shown after a rest", 1f, alpha)
    }

    @Test
    fun `the label fades, it does not jump`() {
        place()
        pass(1500, keyMs = 64)
        val alphas = ArrayList<Float>()
        // the rest, and the fade after it
        repeat(60) {
            pass(FRAME)
            alphas += alpha
        }
        val steps = alphas.zipWithNext { a, b -> b - a }

        assertTrue("from dim to shown: $alphas", alphas.first() < 0.5f && alphas.last() == 1f)
        assertTrue("in small steps: $steps", steps.all { it in -0.001f..0.1f })
    }

    @Test
    fun `a mouse over the label or its cursor shows it`() {
        place()
        pass(1500, keyMs = 64)
        val typing = alpha
        motion.hover = Offset(101f, 110f)
        pass(300, keyMs = 64)
        val overCaret = alpha
        motion.hover = Offset(500f, 500f)
        pass(300, keyMs = 64)
        val away = alpha

        assertTrue(typing < 0.5f)
        assertEquals(1f, overCaret)
        assertEquals(RemoteLabelMotion.DIM_ALPHA, away, 0.01f)
    }

    @Test
    fun `a label that covers the cursor of the user fades out`() {
        place()
        pass(500)
        localCaret = Rect(110f, 85f, 112f, 105f)
        pass(300)
        val covering = alpha
        localCaret = Rect(300f, 85f, 302f, 105f)
        pass(300)

        assertEquals(0f, covering)
        assertEquals(1f, alpha)
    }

    @Test
    fun `a signal of the app shows the label for a moment`() {
        place()
        pass(1500, keyMs = 64)
        val typing = alpha
        signal++
        pass(1000, keyMs = 64)
        val signaled = alpha
        pass(1000, keyMs = 64)

        assertTrue(typing < 0.5f)
        assertEquals("shown by the signal", 1f, signaled)
        assertEquals("faded down after it", RemoteLabelMotion.DIM_ALPHA, alpha, 0.01f)
    }

    @Test
    fun `the app shows the label always or never`() {
        place()
        visibility = KodeEditorLabelVisibility.Shown
        localCaret = Rect(110f, 85f, 112f, 105f)
        pass(1500, keyMs = 64)
        val shown = alpha
        visibility = KodeEditorLabelVisibility.Hidden
        localCaret = null
        pass(1000)

        assertEquals("shown, while she types and over the cursor of the user", 1f, shown)
        assertEquals("hidden, also at rest", 0f, alpha)
    }

    @Test
    fun `a label keeps its side while its writer writes, and changes it after a rest`() {
        val above = caret.top - 20f
        place(below = true)
        val ys = ArrayList<Float>()
        repeat(30) {
            time += FRAME
            if (it % 4 == 0) motion.moved("jane", jumped = false)
            motion.onFrame(time)
            ys += place(below = false)
        }
        val rest = (0 until 50).map {
            time += FRAME
            motion.onFrame(time)
            place(below = false)
        }

        assertTrue("below while she writes: $ys", ys.all { it == caret.bottom })
        assertEquals("above after a rest", above, rest.last())
        assertTrue("on its way: $rest", rest.any { it > above + 1f && it < caret.bottom - 1f })
    }

    @Test
    fun `a jump takes the label to its new side at once`() {
        place(below = true)
        pass(100, keyMs = 64)
        motion.moved("jane", jumped = true)
        time += FRAME
        motion.onFrame(time)

        assertEquals(caret.top - 20f, place(below = false))
    }

    private companion object {
        const val FRAME = 16L
    }
}
