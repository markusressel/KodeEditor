package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.layout.KodeEditorLayout
import de.markusressel.kodeeditor.library.compose.reveal.KodeEditorReveal
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import java.lang.reflect.Proxy
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KodeEditorRevealTest {

    private val before = "# Notes\n"
    private var state = KodeEditorState("${before}after\n")
    private var reveal = KodeEditorReveal(state, "agent")

    /** where the text the agent wrote is hidden */
    private fun hidden() = state.rangeStyles.filter { it.tag.startsWith("agent") }.map { it.start until it.end }

    /** chunks of 3 to 5 words at 0.5 to 2 s, like an agent sends them, one of them with a line break */
    private fun chunks(random: Random): List<Pair<Long, String>> {
        val words = listOf("the", "editor", "reveals", "text", "of", "an", "agent", "chunk", "by", "chunk", "smoothly")
        var time = 0L
        return (0 until 20).map { index ->
            var chunk = (0 until random.nextInt(3, 6)).joinToString(" ") { words.random(random) } + " "
            while (chunk.length < 15) chunk += "and "
            chunk = chunk.take(40)
            if (index == 7) chunk = chunk.replaceFirst(' ', '\n')
            time += random.nextLong(500, 2001)
            time to chunk
        }
    }

    @Test
    fun `chunks are revealed in order, each character once, and none of them waits longer than 4 s`() {
        repeat(5) { seed ->
            state = KodeEditorState("${before}after\n")
            reveal = KodeEditorReveal(state, "agent")
            val arrivals = chunks(Random(seed))
            val arrived = ArrayList<Long>()
            val revealedAt = ArrayList<Long>()
            var written = ""
            var next = 0
            var time = 0L
            var head = before.length
            while (next < arrivals.size || reveal.isRevealing) {
                while (next < arrivals.size && arrivals[next].first <= time) {
                    val shown = written.length - reveal.hiddenLength
                    val chunk = arrivals[next++].second
                    reveal.insert(before.length + written.length, chunk)
                    written += chunk
                    repeat(chunk.length) { arrived += time }
                    assertEquals("a chunk shows nothing before it", shown, written.length - reveal.hiddenLength)
                }
                time += 16
                reveal.advance(16)

                val shown = written.length - reveal.hiddenLength
                while (revealedAt.size < shown) revealedAt += time
                assertEquals("$before${written}after\n", state.text)
                val hidden = hidden()
                if (shown < written.length) {
                    val rest = before.length + shown until before.length + written.length
                    assertEquals("only the rest is hidden", listOf(rest), hidden)
                } else {
                    assertTrue(hidden.isEmpty())
                }
                val now = reveal.head ?: (before.length + written.length)
                assertTrue("the head moves on: $head $now", now >= head)
                head = now
            }
            val waits = arrived.indices.map { revealedAt[it] - arrived[it] }
            assertTrue("seed $seed waits ${waits.max()}", waits.max() <= 4000 + 16)
        }
    }

    @Test
    fun `a lot of text at once is revealed at an even speed within 4 s`() {
        val text = "word ".repeat(400)
        reveal.insert(before.length, text)

        val steps = ArrayList<Int>()
        var hidden = reveal.hiddenLength
        while (reveal.advance(16)) {
            steps += hidden - reveal.hiddenLength
            hidden = reveal.hiddenLength
        }
        steps += hidden

        assertTrue("within 4 s: ${steps.size} frames", steps.size * 16 <= 4000 + 16)
        // the last frame shows what is left
        val even = steps.dropLast(1)
        assertTrue("evenly: $steps", even.max() - even.min() <= 1)
    }

    @Test
    fun `little text is revealed at 60 characters per second`() {
        reveal.insert(before.length, "a short chunk of an agent ")

        var frames = 0
        while (reveal.advance(16)) frames++

        // 26 characters in 433 ms
        assertTrue("$frames frames", frames in 26..28)
    }

    @Test
    fun `a chunk somewhere else waits for the one before it`() {
        reveal.insert(before.length, "first chunk ")
        reveal.advance(48)
        val head = reveal.head!!
        reveal.insert(state.text.length, "second chunk")

        assertEquals(head, reveal.head)
        while (reveal.head!! < before.length + "first chunk ".length - 1) reveal.advance(16)
        reveal.advance(32)
        assertTrue("then the other one: ${reveal.head}", reveal.head!! > state.text.indexOf("second"))
    }

    @Test
    fun `a chunk inside the hidden text joins it`() {
        reveal.insert(before.length, "one three ")
        reveal.insert(before.length + 4, "two ")

        assertEquals("$before" + "one two three after\n", state.text)
        assertEquals(listOf(before.length until before.length + 14), hidden())
    }

    @Test
    fun `stop shows all of it`() {
        reveal.insert(before.length, "a chunk of an agent")
        reveal.advance(16)
        reveal.stop()

        assertFalse(reveal.isRevealing)
        assertNull(reveal.head)
        assertTrue(hidden().isEmpty())
    }

    @Test
    fun `the text of other writers is not touched`() {
        val other = KodeEditorReveal(state, "other")
        other.insert(0, "theirs ")
        reveal.insert(state.text.length, "mine")
        reveal.stop()

        assertTrue(other.isRevealing)
        assertEquals(0, other.head)
    }

    @Test
    fun `a collapsed part is revealed as a whole, as one character`() {
        reveal.insert(before.length, "a[link](url)b")
        val link = TextRange(before.length + 1, before.length + 12)
        reveal.layout = collapsing(link)

        reveal.advance(17)
        assertEquals(link.min, reveal.head)
        reveal.advance(17)
        assertEquals(link.max, reveal.head)
    }

    /** a layout that only has the [collapsed] part */
    private fun collapsing(collapsed: TextRange) = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(KodeEditorLayout::class.java),
    ) { _, method, args ->
        check(method.name.startsWith("collapsedRangeAt")) { method.name }
        collapsed.takeIf { args[0] as Int in collapsed.min until collapsed.max }
    } as KodeEditorLayout

    /** a frame each time the reveal waits for one, 16 ms apart */
    private class TestFrameClock : MonotonicFrameClock {
        var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            return onFrame(++frames * 16_000_000L)
        }
    }

    /** runs [keepRevealing][KodeEditorReveal.keepRevealing] with its own frame clock while [block] runs */
    private fun revealing(block: suspend (TestFrameClock) -> Unit) {
        val clock = TestFrameClock()
        runBlocking(clock) {
            val job = launch { reveal.keepRevealing() }
            yield()
            block(clock)
            job.cancel()
        }
    }

    /** lets the frames run until [frames] passed */
    private suspend fun TestFrameClock.until(frames: Int) {
        Snapshot.sendApplyNotifications()
        while (this.frames < frames) yield()
    }

    @Test
    fun `the frames of the frame clock of the coroutine reveal the text`() = revealing { clock ->
        reveal.insert(before.length, "a chunk of an agent")
        clock.until(5)

        // 0.96 characters in each frame
        assertEquals(19 - 4, reveal.hiddenLength)
    }

    @Test
    fun `run again, it goes on where it was`() {
        revealing { clock ->
            reveal.insert(before.length, "a chunk of an agent")
            clock.until(5)
        }
        revealing { clock -> clock.until(5) }

        assertEquals(19 - 9, reveal.hiddenLength)
    }

    @Test
    fun `a change of somebody else inside the hidden text or at its edge shows it`() {
        for (at in listOf(4, 0, 19)) {
            state = KodeEditorState("${before}after\n")
            reveal = KodeEditorReveal(state, "agent")
            revealing { clock ->
                reveal.insert(before.length, "a chunk of an agent")
                clock.until(1)
                state.applyRemote(listOf(TextSplice(before.length + at, 0, "x")))

                assertFalse("at $at", reveal.isRevealing)
            }
        }
    }

    @Test
    fun `a change of somebody else elsewhere moves the hidden text`() = revealing { clock ->
        reveal.insert(before.length, "a chunk of an agent")
        clock.until(1)
        state.applyRemote(listOf(TextSplice(0, 0, "x")))
        state.applyRemote(listOf(TextSplice(state.text.length, 0, "y")))

        assertTrue(reveal.isRevealing)
        assertEquals(before.length + 1, reveal.head)
    }

    @Test
    fun `a change of the user shows all of it`() = revealing { clock ->
        reveal.insert(before.length, "a chunk of an agent")
        clock.until(1)
        state.setSelection(androidx.compose.ui.text.TextRange(0))
        state.replaceSelection("x")

        assertFalse(reveal.isRevealing)
    }

    @Test
    fun `a character of two UTF-16 units is revealed as a whole`() {
        val chunk = "a\uD83D\uDE00b\uD83D\uDE00\uD83D\uDE00c"
        reveal.insert(before.length, chunk)
        var frames = 0
        while (reveal.isRevealing) {
            reveal.advance(16)
            frames++
            val start = reveal.head ?: break
            assertFalse("the hidden text starts inside a pair at $start", state.text[start].isLowSurrogate())
        }
        // about one character in each frame at 60 characters per second, a pair counts as one
        assertTrue("$frames frames", frames <= chunk.codePointCount(0, chunk.length) + 1)
    }
}
