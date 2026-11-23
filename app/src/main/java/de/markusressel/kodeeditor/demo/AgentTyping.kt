package de.markusressel.kodeeditor.demo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.kodeeditor.library.compose.TextSplice

/**
 * Shows text of somebody else as if it was typed: the whole text is in the editor at once, the part that is not typed
 * yet is drawn transparent and keeps its space, so nothing below it moves. What was just typed fades in. The editor
 * moves the hidden and the fading ranges with every change of the text, and any change of the user ends the typing at
 * once.
 *
 * @param state the text, add this as its listener
 */
class AgentTyping(private val state: KodeEditorState) : KodeEditorListener {

    /** how long typed text takes to fade in, 0 shows it at once */
    var fadeMs = 250

    /** how many of the last typed characters can fade at most, older ones are shown at once */
    var fadeWindow = 16

    private val hidden get() = state.rangeStyles.firstOrNull { it.tag == TAG }

    /** where the agent types at the moment, null when it does not type */
    val head: Int? get() = hidden?.start

    /** whether typed text is still fading in */
    val fading: Boolean get() = state.rangeAlphas.any { it.tag.startsWith(FADE_TAG) }

    /** inserts [text] at [position], all of it hidden */
    fun start(text: String, position: Int) {
        stop()
        state.applyRemote(listOf(TextSplice(position, 0, text)))
        // in the same call as the change, so no frame shows the whole text
        state.setRangeStyles(state.rangeStyles + AnnotatedString.Range(HIDDEN, position, position + text.length, TAG))
    }

    /**
     * @param steps how many characters to show, a collapsed part counts as one, as it shows up as a whole
     * @param nowMs the time of the frame
     * @param collapsedRangeAt the collapsed part at a position, see KodeEditorLayout.collapsedRangeAt
     * @return whether there is more to show
     */
    fun reveal(steps: Int, nowMs: Long, collapsedRangeAt: (Int) -> TextRange? = { null }): Boolean {
        val range = hidden ?: return false
        var start = range.start
        repeat(steps) {
            if (start >= range.end) return@repeat
            val collapsed = collapsedRangeAt(start)
            start = if (collapsed != null && collapsed.max > start) collapsed.max else start + 1
        }
        start = start.coerceAtMost(range.end)
        val others = state.rangeStyles - range
        val more = start < range.end
        state.setRangeStyles(if (more) others + AnnotatedString.Range(HIDDEN, start, range.end, TAG) else others)
        if (fadeMs > 0) {
            state.setRangeAlphas(state.rangeAlphas + AnnotatedString.Range(0f, range.start, start, "$FADE_TAG$nowMs"))
        }
        return more
    }

    /**
     * Updates how visible the typed text is.
     *
     * @param nowMs the time of the frame
     */
    fun fade(nowMs: Long) {
        val ranges = state.rangeAlphas
        if (ranges.isEmpty()) return
        // the window counts back from where the newest fading text ends
        val newestEnd = ranges.maxOf { it.end }
        val updated = ranges.mapNotNull { range ->
            if (!range.tag.startsWith(FADE_TAG)) return@mapNotNull range
            val born = range.tag.removePrefix(FADE_TAG).toLong()
            val progress = if (fadeMs > 0) (nowMs - born).toFloat() / fadeMs else 1f
            if (progress >= 1f || range.end <= newestEnd - fadeWindow) return@mapNotNull null
            // fast at first, so new text does not stay invisible for long
            val eased = 1f - (1f - progress.coerceAtLeast(0f)).let { it * it }
            AnnotatedString.Range(eased, range.start, range.end, range.tag)
        }.takeLast(MAX_FADING)
        state.setRangeAlphas(updated)
    }

    /** shows all of the text */
    fun stop() {
        state.setRangeStyles(state.rangeStyles.filter { it.tag != TAG })
        state.setRangeAlphas(state.rangeAlphas.filter { !it.tag.startsWith(FADE_TAG) })
    }

    override fun onLocalEdit(splice: TextSplice) = stop()

    private companion object {
        const val TAG = "agent"
        const val FADE_TAG = "agent-fade:"
        val HIDDEN = SpanStyle(color = Color.Transparent)

        /** each fading range is a layer of its own when drawn, so there are never many */
        const val MAX_FADING = 24
    }
}

/** what the agent of the demo types, markdown with a bit of everything, so its colors and folds show */
const val AGENT_TEXT = """## Written by an agent

This paragraph is **typed** by an agent, one piece at a time. The text below
does not move, the hidden part keeps its space.

- `code` keeps its colors
- [links](https://github.com/markusressel/KodeEditor) appear as a whole when they are collapsed

"""
