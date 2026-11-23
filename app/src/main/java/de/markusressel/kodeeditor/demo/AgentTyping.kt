package de.markusressel.kodeeditor.demo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import de.markusressel.kodeeditor.library.compose.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.kodeeditor.library.compose.TextSplice

/**
 * Shows text of somebody else as if it was typed: the whole text is in the editor at once, the part that is not typed
 * yet is drawn transparent and keeps its space, so nothing below it moves. The editor moves the hidden range with every
 * change of the text, and any change of the user ends the typing at once.
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
     * @param characters how many characters to show
     * @return whether there is more to show
     */
    fun reveal(characters: Int): Boolean {
        val range = hidden ?: return false
        val others = state.rangeStyles - range
        val start = range.start + characters
        val more = start < range.end
        state.setRangeStyles(if (more) others + AnnotatedString.Range(HIDDEN, start, range.end, TAG) else others)
        return more
    }

    /** shows all of the text */
    fun stop() {
        state.setRangeStyles(state.rangeStyles.filter { it.tag != TAG })
    }

    override fun onLocalEdit(splice: TextSplice) = stop()

    private companion object {
        const val TAG = "agent"
        val HIDDEN = SpanStyle(color = Color.Transparent)
    }
}

/** what the agent of the demo types, markdown with a bit of everything, so its colors show */
const val AGENT_TEXT = """## Written by an agent

This paragraph is **typed** by an agent, one piece at a time. The text below
does not move, the hidden part keeps its space.

- `code` keeps its colors
- [links](https://github.com/markusressel/KodeEditor) too

"""
