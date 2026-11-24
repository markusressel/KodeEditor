package de.markusressel.kodeeditor.library.compose.input

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import de.markusressel.kodeeditor.library.compose.fold.EditorFolds
import de.markusressel.kodeeditor.library.compose.input.EditorClipboardActions
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState

/**
 * What the keyboard (IME) asks the editor for besides text: keys, its context menu and deletes into folded text.
 *
 * @param view the view of the editor, whose window gets the keys
 * @param state the text
 * @param menu copy, cut, paste and select all
 * @param folds the folds of the editor, null if it does not fold
 */
internal class EditorKeyboardActions(
    view: View,
    private val state: KodeEditorState,
    private val menu: EditorClipboardActions,
    private val folds: () -> EditorFolds?,
) : KeyboardActions {
    private val keySender = BaseInputConnection(view, false)

    // on the queue of the window, like a key of a hardware keyboard, which ends up in the key handler of the editor
    override fun onKeyEvent(event: AndroidKeyEvent) = keySender.sendKeyEvent(event)

    override fun openFoldInsteadOfDelete(backward: Boolean) = folds()?.inline?.openInsteadOfDelete(backward) == true

    override fun onContextMenuAction(id: Int): Boolean {
        when (id) {
            android.R.id.selectAll -> menu.selectAll()
            android.R.id.copy -> if (!state.selection.collapsed) menu.copy()
            android.R.id.cut -> if (!state.selection.collapsed) menu.cut()
            android.R.id.paste, android.R.id.pasteAsPlainText -> menu.paste()
            android.R.id.undo -> state.undo()
            android.R.id.redo -> state.redo()
            else -> return false
        }
        return true
    }
}
