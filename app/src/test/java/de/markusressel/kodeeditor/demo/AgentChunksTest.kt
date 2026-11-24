package de.markusressel.kodeeditor.demo

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import de.markusressel.kodeeditor.library.compose.reveal.KodeEditorReveal
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun `chunks come quickly, in bursts, and after pauses`() {
        val gaps = agentGaps(200, Random(AgentPace.SEED))

        assertEquals(199, gaps.size)
        assertTrue(gaps.all { it in AgentPace.BURST_GAP_MS || it in AgentPace.GAP_MS || it in AgentPace.PAUSE_MS })
        assertTrue(gaps.count { it in AgentPace.BURST_GAP_MS } >= 10)
        assertTrue(gaps.count { it in AgentPace.PAUSE_MS } >= 10)
    }

    /** a frame of 16 ms each time the test lets one pass */
    private class TestFrameClock : MonotonicFrameClock {
        var timeMs = 0L
        private var frameDue = false

        private companion object {
            const val FRAME_TURNS = 5
        }

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            while (!frameDue) yield()
            frameDue = false
            return onFrame(timeMs * 1_000_000)
        }

        /** lets the frames of [ms] pass */
        suspend fun pass(ms: Long) {
            val end = timeMs + ms
            while (timeMs < end) {
                Snapshot.sendApplyNotifications()
                timeMs += 16
                frameDue = true
                // the reveal takes the frame within a few turns, while it does not wait for frames nobody takes it
                repeat(FRAME_TURNS) { if (frameDue) yield() }
                frameDue = false
            }
        }
    }

    private class Run(val state: KodeEditorState, val backlogs: List<Int>)

    /**
     * lets the agent write [text] at the start of the text of [state], with the pace of the demo
     *
     * @param backlogs how much of the text is hidden when each chunk but the first one arrives
     */
    private fun agentRun(state: KodeEditorState, text: String, seed: Int, backlogs: MutableList<Int>) {
        val clock = TestFrameClock()
        runBlocking(clock) {
            val reveal = KodeEditorReveal(state, "agent")
            // a frame clock that only runs while it is asked to, so the loop of the reveal waits for it
            val revealing = launch { reveal.keepRevealing() }
            yield()
            agentWrites(reveal, 0, text, chunked = true, random = Random(seed)) { ms ->
                clock.pass(ms)
                backlogs += reveal.hiddenLength
            }
            while (reveal.isRevealing) clock.pass(16)
            revealing.cancel()
        }
    }

    @Test
    fun `the next chunk mostly comes while the ones before are still revealed`() {
        for (seed in listOf(AgentPace.SEED, 9, 14, 18)) {
            val state = KodeEditorState("\nthe text below")
            val backlogs = ArrayList<Int>()
            agentRun(state, AGENT_LONG_TEXT, seed, backlogs)

            assertEquals(AGENT_LONG_TEXT + "\nthe text below", state.text)
            val waiting = backlogs.count { it > 0 }.toFloat() / backlogs.size
            assertTrue("seed $seed, most of them: $backlogs", waiting >= 0.9f)
            assertTrue("seed $seed, a few hundred characters: $backlogs", backlogs.max() >= 200)
            assertTrue("seed $seed, all of it shown in a pause: $backlogs", backlogs.contains(0))
        }
    }

    @Test
    fun `an agent that writes again writes behind its own text, not into the one before`() {
        val state = KodeEditorState("\nthe text below")
        agentRun(state, AGENT_TEXT, AgentPace.SEED, ArrayList())
        agentRun(state, AGENT_TEXT, AgentPace.SEED, ArrayList())

        assertEquals(AGENT_TEXT + AGENT_TEXT + "\nthe text below", state.text)
    }

    @Test
    fun `the closest one of several`() {
        assertEquals(6, closestIndexOf("ab ab ab", "ab", 5))
        assertEquals(null, closestIndexOf("ab", "c", 0))
    }
}
