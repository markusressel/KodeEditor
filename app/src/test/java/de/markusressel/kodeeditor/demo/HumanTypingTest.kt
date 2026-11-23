package de.markusressel.kodeeditor.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HumanTypingTest {

    private val text = AGENT_LONG_TEXT

    @Test
    fun `typed with its typos, the keys give the text`() {
        for (seed in 0 until 20) {
            assertEquals("seed $seed", text, humanKeystrokes(text, Random(seed)).typed())
        }
    }

    @Test
    fun `a key comes about 150 ms after a letter`() {
        val keys = humanKeystrokes(text)
        // the keys behind a letter, without the typos and what follows them
        val gaps = keys.zipWithNext().filter { (before, key) ->
            val char = (before.key as? Keystroke.Type)?.char
            char != null && char.isLetter() && key.key is Keystroke.Type && key.delayMs < TypingPace.THINK_MS.first
        }.map { it.second.delayMs }.sorted()
        val median = gaps[gaps.size / 2]

        assertTrue("median $median", median in 110L..170L)
        assertTrue("from ${gaps.first()} to ${gaps.last()}", gaps.first() >= TypingPace.KEY_MS.first)
    }

    @Test
    fun `a key after a punctuation mark comes later`() {
        val keys = humanKeystrokes(text)
        val afterPunctuation = keys.zipWithNext().filter { (before, key) ->
            (before.key as? Keystroke.Type)?.char in listOf('.', ',') && key.key is Keystroke.Type
        }.map { it.second.delayMs }

        assertTrue(afterPunctuation.toString(), afterPunctuation.isNotEmpty())
        assertTrue(afterPunctuation.toString(), afterPunctuation.all { it >= TypingPace.AFTER_PUNCTUATION_MS.first })
    }

    @Test
    fun `now and then a person thinks, mistypes and types in a burst`() {
        val keys = humanKeystrokes(text)

        assertTrue("thinks", keys.any { it.delayMs >= TypingPace.THINK_MS.first })
        assertTrue("mistypes", keys.count { it.key == Keystroke.Backspace } >= 2)
        // keys of a burst come at half the time
        assertTrue("bursts", keys.count { it.delayMs < 80 } >= 5)
    }

    @Test
    fun `Jane mistypes in the demo`() {
        val keys = humanKeystrokes(JANE_TEXT)

        assertTrue(keys.toString(), keys.any { it.key == Keystroke.Backspace })
        assertEquals(JANE_TEXT, keys.typed())
    }
}
