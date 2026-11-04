package de.markusressel.kodeeditor.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class LineEditTest {
    private fun lines(old: String, new: String): List<Int?> {
        val edit = LineEdit(old, new)
        return (0..old.count { it == '\n' }).map { edit.lineAfterEdit(it) }
    }

    @Test
    fun `typing within a line moves nothing`() {
        assertEquals(listOf(0, 1), lines("a\nb", "ax\nb"))
    }

    @Test
    fun `a new line above moves the lines below`() {
        assertEquals(listOf(0, 2), lines("a\nb", "a\n\nb"))
    }

    @Test
    fun `a line break at the start of a line takes the line along`() {
        assertEquals(listOf(1), lines("a", "\na"))
    }

    @Test
    fun `a line break in the middle of a line leaves it at its start`() {
        assertEquals(listOf(0, 2), lines("ab\nc", "a\nb\nc"))
    }

    @Test
    fun `joining two lines moves the second onto the first`() {
        assertEquals(listOf(0, 0, 1), lines("a\nb\nc", "ab\nc"))
    }

    @Test
    fun `a removed line is gone and the next one moves up`() {
        assertEquals(listOf(0, null, 1), lines("a\nb\nc", "a\nc"))
    }

    @Test
    fun `removing an empty line moves the next one up`() {
        assertEquals(listOf(0, null, 1), lines("a\n\nb", "a\nb"))
    }

    @Test
    fun `replacing everything leaves nothing`() {
        assertEquals(listOf(null, null), lines("a\nb", "x\ny\nz"))
    }

    @Test
    fun `pasting several lines moves the rest of the line to the end of the paste`() {
        assertEquals(listOf(0, 3), lines("ab\nc", "ax\ny\nb\nc"))
    }

    @Test
    fun `a line that does not exist is gone`() {
        assertEquals(null, LineEdit("a", "a\nb").lineAfterEdit(5))
    }
}
