package de.markusressel.kodeeditor.demo

import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.reveal.KodeEditorReveal
import kotlin.math.abs
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong
import kotlinx.coroutines.delay

/**
 * Splits text into chunks like an agent sends them: 3 to 5 words, 15 to 40 characters where the words allow it. They
 * end anywhere in a line, and some of them go over the end of a line.
 *
 * @param text what the agent writes
 * @param random picks the number of words
 */
internal fun agentChunks(text: String, random: Random): List<String> {
    val words = Regex("""\S+\s*|\s+""").findAll(text).map { it.value }.toList()
    val chunks = ArrayList<String>()
    var index = 0
    while (index < words.size) {
        val count = random.nextInt(3, 6)
        var chunk = ""
        var taken = 0
        while (index < words.size && (taken < count || chunk.length < 15) && chunk.length + words[index].length <= 40) {
            chunk += words[index++]
            taken++
        }
        // a word longer than a chunk
        if (chunk.isEmpty()) chunk = words[index++]
        chunks += chunk
    }
    return chunks
}

/**
 * How fast the chunks of the agent of the demo arrive: mostly quickly, so a new chunk often comes while the one before
 * is still revealed and the hidden text piles up to a few hundred characters, with short bursts, and with pauses in
 * which all of it is shown before the next chunk comes.
 */
internal object AgentPace {
    /** the usual time between two chunks, in ms */
    val GAP_MS = 100L..350L

    /** how often a burst comes, of [BURST_CHUNKS] chunks */
    const val BURST_CHANCE = 0.2

    /** how many chunks come after the first one of a burst */
    val BURST_CHUNKS = 1..2

    /** the time between the chunks of a burst, in ms */
    val BURST_GAP_MS = 25L..50L

    /** how often a pause comes */
    const val PAUSE_CHANCE = 0.08

    /** how long a pause is, in ms */
    val PAUSE_MS = 1000L..1500L

    /** the same chunks and times each time, so the demo and the showcase look the same each time */
    const val SEED = 33
}

/**
 * @param count how many chunks there are
 * @param random picks the times
 * @return how long to wait before each chunk after the first one, in ms, see [AgentPace]
 */
internal fun agentGaps(count: Int, random: Random): List<Long> {
    val gaps = ArrayList<Long>()
    while (gaps.size < count - 1) {
        val roll = random.nextDouble()
        when {
            roll < AgentPace.BURST_CHANCE -> repeat(random.nextInt(AgentPace.BURST_CHUNKS)) {
                gaps += random.nextLong(AgentPace.BURST_GAP_MS)
            }

            roll < AgentPace.BURST_CHANCE + AgentPace.PAUSE_CHANCE -> {
                gaps += random.nextLong(AgentPace.PAUSE_MS)
            }

            else -> gaps += random.nextLong(AgentPace.GAP_MS)
        }
    }
    return gaps.take(count - 1)
}

/**
 * Lets the agent of the demo write [text] behind [position], with [reveal]. As one chunk, or in chunks of a few words
 * at the pace of [AgentPace], also while the ones before are still revealed. Ends early when the text the agent wrote
 * is gone, e.g. as the user changed it.
 *
 * @param reveal shows the text
 * @param position where the text starts
 * @param text what the agent writes
 * @param chunked whether it arrives in chunks
 * @param random picks the chunks and the time between them, the same one gives the same chunks
 * @param wait waits for the next chunk, for the given ms
 */
internal suspend fun agentWrites(
    reveal: KodeEditorReveal,
    position: Int,
    text: String,
    chunked: Boolean,
    random: Random = Random(AgentPace.SEED),
    wait: suspend (Long) -> Unit = { delay(it) },
) {
    val state = reveal.state
    val chunks = if (chunked) agentChunks(text, random) else listOf(text)
    val gaps = agentGaps(chunks.size, random)
    var written = ""
    var start = position
    chunks.forEachIndexed { index, chunk ->
        if (index > 0) wait(gaps[index - 1])
        // behind what it wrote, which moves with the changes of others. The same text may be there more than once, from
        // an agent before, so it is the one closest to where it was
        start = if (written.isEmpty()) start else closestIndexOf(state.text, written, start) ?: return
        reveal.insert(start + written.length, chunk)
        written += chunk
    }
}

/** @return where [part] is in [text], the one closest to [near], null if it is not there */
internal fun closestIndexOf(text: String, part: String, near: Int): Int? {
    var best: Int? = null
    var index = text.indexOf(part)
    while (index >= 0) {
        if (best == null || abs(index - near) < abs(best - near)) best = index
        index = text.indexOf(part, index + 1)
    }
    return best
}

/** what the agent of the demo types, markdown with a bit of everything, so its colors and folds show */
const val AGENT_TEXT = """## Written by an agent

This paragraph is **typed** by an agent, one piece at a time. The text below
does not move, the hidden part keeps its space.

- `code` keeps its colors
- [links](https://github.com/markusressel/KodeEditor) appear as a whole when they are collapsed

"""

/**
 * What the agent of the demo writes in "Agent chunks": long enough that its hidden text piles up to a few hundred
 * characters, with long lines that wrap
 */
const val AGENT_LONG_TEXT = """## An agent answers

Chunks of an answer arrive a few words at a time, sometimes quickly one after the other, sometimes after a pause. Each
one is hidden at once and keeps its space, so the text below it moves only once, when the chunk arrives, and then
stays where it is while the chunk is typed out.

When more text waits, the reveal gets faster, so no chunk waits longer than four seconds. When the agent pauses, the
reveal catches up, and the cursor of the agent stops at the end of what it wrote until the next chunk comes.

- the cursor glides behind the revealed text, about a character behind it, at an even speed
- the text behind the cursor slides out of the way, also into the next row when a line wraps anew
- `code`, **bold** text and [links](https://github.com/markusressel/KodeEditor) keep their colors

A long line like this one goes on and on, so it wraps into the next row of the editor while it is typed, and the cursor
of the agent follows it into that row, while the rest of the text waits below.

Then the agent sums it up: the chunks arrive in bursts and pauses, the reveal keeps an even pace and speeds up when the
hidden text piles up, and the text below never jumps. Type anywhere to see all of it at once.

"""
