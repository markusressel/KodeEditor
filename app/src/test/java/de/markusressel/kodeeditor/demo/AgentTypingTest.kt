package de.markusressel.kodeeditor.demo

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.kodeeditor.library.compose.TextSplice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTypingTest {

    private val state = KodeEditorState("one\ntwo")
    private val agent = AgentTyping(state).also { state.addListener(it) }

    private val hidden get() = state.rangeStyles.map { it.start to it.end }

    @Test
    fun `the text is there at once, and hidden until it is revealed`() {
        agent.start("abc\n", 4)
        assertEquals("one\nabc\ntwo", state.text)
        assertEquals(listOf(4 to 8), hidden)
        assertTrue(agent.reveal(2, nowMs = 0))
        assertEquals(6, agent.head)
        assertFalse(agent.reveal(2, nowMs = 33))
        assertNull(agent.head)
        assertEquals("one\nabc\ntwo", state.text)
    }

    @Test
    fun `the hidden part moves with changes in front of it`() {
        agent.start("abc\n", 4)
        state.applyRemote(listOf(TextSplice(0, 0, ">")))
        assertEquals(listOf(5 to 9), hidden)
    }

    @Test
    fun `typing of the user ends the typing of the agent at once`() {
        agent.start("abc\n", 4)
        state.setSelection(TextRange(0))
        state.replaceSelection("x")
        assertNull(agent.head)
        assertEquals(emptyList<Pair<Int, Int>>(), hidden)
        assertEquals("xone\nabc\ntwo", state.text)
    }

    @Test
    fun `a collapsed part is revealed as a whole, as one step`() {
        agent.start("a[link](url)b\n", 4)
        agent.reveal(1, nowMs = 0)
        assertEquals(5, agent.head)
        agent.reveal(1, nowMs = 33) { if (it in 5 until 16) TextRange(5, 16) else null }
        assertEquals(16, agent.head)
    }

    @Test
    fun `typed text fades in, and is shown at once when it leaves the window`() {
        agent.fadeMs = 100
        agent.fadeWindow = 2
        agent.start("abcdef\n", 4)
        agent.reveal(2, nowMs = 0)
        assertEquals(listOf(AnnotatedString.Range(0f, 4, 6, "agent-fade:0")), state.rangeAlphas)
        agent.fade(nowMs = 50)
        assertEquals(0.75f, state.rangeAlphas.single().item, 0.001f)
        agent.reveal(2, nowMs = 50)
        agent.fade(nowMs = 60)
        assertEquals("the first part left the window", listOf(6 to 8), state.rangeAlphas.map { it.start to it.end })
        agent.fade(nowMs = 150)
        assertTrue(state.rangeAlphas.isEmpty())
        assertFalse(agent.fading)
    }

    @Test
    fun `without a fade time nothing fades`() {
        agent.fadeMs = 0
        agent.start("abc\n", 4)
        agent.reveal(2, nowMs = 0)
        assertTrue(state.rangeAlphas.isEmpty())
    }
}
