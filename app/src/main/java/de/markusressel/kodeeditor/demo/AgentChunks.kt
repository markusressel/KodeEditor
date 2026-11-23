package de.markusressel.kodeeditor.demo

import de.markusressel.kodeeditor.library.compose.KodeEditorReveal
import kotlinx.coroutines.delay
import kotlin.random.Random

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
 * Lets the agent of the demo write [text] behind [position], with [reveal]. As one chunk, or in chunks of a few words
 * that arrive 0.5 to 2 s apart, also while the one before is still revealed. Ends early when the text the agent wrote
 * is gone, e.g. as the user changed it.
 *
 * @param reveal shows the text
 * @param position where the text starts
 * @param text what the agent writes
 * @param chunked whether it arrives in chunks
 * @param random picks the chunks and the time between them, the same one gives the same chunks
 */
internal suspend fun agentWrites(
    reveal: KodeEditorReveal,
    position: Int,
    text: String,
    chunked: Boolean,
    random: Random = Random(AGENT_SEED),
) {
    val state = reveal.state
    val chunks = if (chunked) agentChunks(text, random) else listOf(text)
    var written = ""
    chunks.forEachIndexed { index, chunk ->
        if (index > 0) delay(random.nextLong(500, 2001))
        // behind what it wrote, which moves with the changes of others
        val at = if (written.isEmpty()) position else state.text.indexOf(written).takeIf { it >= 0 } ?: return
        reveal.insert(at + written.length, chunk)
        written += chunk
    }
}

/** the same chunks each time, so the showcase looks the same in each recording */
private const val AGENT_SEED = 7

/** what the agent of the demo types, markdown with a bit of everything, so its colors and folds show */
const val AGENT_TEXT = """## Written by an agent

This paragraph is **typed** by an agent, one piece at a time. The text below
does not move, the hidden part keeps its space.

- `code` keeps its colors
- [links](https://github.com/markusressel/KodeEditor) appear as a whole when they are collapsed

"""
