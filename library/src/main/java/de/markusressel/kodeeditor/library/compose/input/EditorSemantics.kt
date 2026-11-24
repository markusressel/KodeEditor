package de.markusressel.kodeeditor.library.compose.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState

/**
 * What accessibility services see of the editor and can do with it: the text, the selection, setting the selection,
 * a click that focuses it, and setting and inserting text while it can be edited.
 *
 * @param state the text
 * @param enabled whether the editor is enabled
 * @param readOnly whether the user can not change the text
 * @param focus focuses the editor and shows the keyboard, for a click
 */
internal fun Modifier.editorSemantics(
    state: KodeEditorState,
    enabled: Boolean,
    readOnly: Boolean,
    focus: () -> Unit,
) = semantics {
    if (!enabled) disabled()
    editableText = AnnotatedString(state.text)
    textSelectionRange = state.selection
    setSelection { start, end, _ ->
        if (!enabled || start !in 0..state.rawText.length || end !in 0..state.rawText.length) {
            return@setSelection false
        }
        state.setSelection(TextRange(start, end))
        true
    }
    onClick {
        if (!enabled) return@onClick false
        focus()
        true
    }
    if (enabled && !readOnly) {
        setText {
            state.setText(it.text)
            true
        }
        insertTextAtCursor {
            state.replaceSelection(it.text)
            true
        }
    }
}
