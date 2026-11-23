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
 * yet is drawn transparent and keeps its space, so nothing below it moves. Each typed piece appears with the appearance
 * animation of the editor, if it has one. The editor moves the hidden range with every change of the text, and a change
 * of the user shows the rest of the text at once.
 *
 * @param state the text, add this as its listener
 */
class AgentTyping(private val state: KodeEditorState) : KodeEditorListener {

    private val hidden get() = state.rangeStyles.firstOrNull { it.tag == TAG }

    /** where the agent types at the moment, null when it does not type */
    val head: Int? get() = hidden?.start

    /** inserts [text] at [position], all of it hidden */
    fun start(text: String, position: Int) {
        stop()
        state.applyRemote(listOf(TextSplice(position, 0, text)))
        // in the same call as the change, so no frame shows the whole text
        state.setRangeStyles(state.rangeStyles + AnnotatedString.Range(HIDDEN, position, position + text.length, TAG))
    }

    /**
     * @param steps how many characters to show, a collapsed part counts as one, as it shows up as a whole
     * @param collapsedRangeAt the collapsed part at a position, see KodeEditorLayout.collapsedRangeAt
     * @return whether there is more to show
     */
    fun reveal(steps: Int, collapsedRangeAt: (Int) -> TextRange? = { null }): Boolean {
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
        state.appear(range.start, start, TAG)
        return more
    }

    /** shows all of the text at once */
    fun stop() {
        state.setRangeStyles(state.rangeStyles.filter { it.tag != TAG })
        state.finishAppearing(TAG)
    }

    /** the user takes over, what the agent wrote is all there at once. What others write keeps appearing */
    override fun onLocalEdit(splice: TextSplice) = stop()

    private companion object {
        const val TAG = "agent"
        val HIDDEN = SpanStyle(color = Color.Transparent)
    }
}

/** what the agent of the demo types, markdown with a bit of everything, so its colors and folds show */
const val AGENT_TEXT = """## Written by an agent

This paragraph is **typed** by an agent, one piece at a time. The text below
does not move, the hidden part keeps its space.

- `code` keeps its colors
- [links](https://github.com/markusressel/KodeEditor) appear as a whole when they are collapsed

"""
