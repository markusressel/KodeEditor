package de.markusressel.kodeeditor.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AgentChunksTest {

    private val chunks = agentChunks(AGENT_TEXT, Random(7))

    @Test
    fun `the chunks are all of the text, in its order`() {
        assertEquals(AGENT_TEXT, chunks.joinToString(""))
    }

    @Test
    fun `a chunk has a few words`() {
        // the last one has what is left, a link is a single word
        val words = chunks.dropLast(1).filter { chunk -> chunk.trim().any { it.isWhitespace() } }
        assertTrue(chunks.toString(), words.all { it.length in 15..40 })
    }

    @Test
    fun `chunks end in a line, and some go over the end of one`() {
        assertTrue(chunks.toString(), chunks.any { !it.endsWith('\n') })
        assertTrue(chunks.toString(), chunks.any { it.trimEnd().contains('\n') })
    }
}
