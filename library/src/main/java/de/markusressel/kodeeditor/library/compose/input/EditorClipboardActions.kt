package de.markusressel.kodeeditor.library.compose.input

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState

/**
 * Copy, cut, paste and select all, for the text menu, the keys and the keyboard.
 */
internal class EditorClipboardActions(
    private val state: KodeEditorState,
    private val readOnly: Boolean,
    private val clipboardText: () -> AnnotatedString?,
    private val setClipboardText: (AnnotatedString) -> Unit,
) {
    private val selectedText get() = state.rawText.substring(state.selection.min, state.selection.max)

    fun copy() {
        setClipboardText(AnnotatedString(selectedText))
        state.setSelection(TextRange(state.selection.max))
    }

    fun cut() {
        if (readOnly) return
        setClipboardText(AnnotatedString(selectedText))
        state.replaceSelection("")
    }

    fun paste() {
        if (readOnly) return
        val text = clipboardText()?.text ?: return
        state.bigChange { state.replaceSelection(text) }
    }

    fun selectAll() = state.selectAll()
}
