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
        assertTrue(agent.reveal(2))
        assertEquals(6, agent.head)
        assertFalse(agent.reveal(2))
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
        agent.reveal(1)
        assertEquals(5, agent.head)
        agent.reveal(1) { if (it in 5 until 16) TextRange(5, 16) else null }
        assertEquals(16, agent.head)
    }
}
