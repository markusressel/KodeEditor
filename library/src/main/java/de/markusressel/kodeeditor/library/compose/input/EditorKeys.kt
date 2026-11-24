package de.markusressel.kodeeditor.library.compose.input

import android.view.KeyCharacterMap
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.semantics.setSelection
import de.markusressel.kodeeditor.library.compose.cursor.HandleMenu
import de.markusressel.kodeeditor.library.compose.fold.EditorFolds
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState

/** what a zoom key asks for */
internal enum class KeyZoom { In, Out, Reset }

/**
 * Runs the keys of a hardware keyboard on the text: moving the cursor, typing, deleting, the clipboard, undo, folding
 * and zooming.
 *
 * @param state the text
 * @param menu copy, cut, paste and select all
 * @param cursor moves the cursor with the keys
 */
internal class EditorKeys(
    private val state: KodeEditorState,
    private val menu: HandleMenu,
    private val cursor: KeyboardCursor,
) {
    /**
     * @param event a key that went down on a text keyboard
     * @param editable whether the user may change the text
     * @param keyBindings what the keys do
     * @param folds the folds of the editor, null if it does not fold
     * @param lines the rows the cursor moves through
     * @param pageHeight how far a page up or down moves, in the coordinates of the text
     * @param onCursorMoved called when a key moved the cursor
     * @param zoom zooms the view, for the zoom keys where there is nothing to fold
     * @return whether the key was used, the rest of the app gets it otherwise
     */
    fun onKeyDown(
        event: KeyEvent,
        editable: Boolean,
        keyBindings: List<KodeEditorKeyBinding>,
        folds: EditorFolds?,
        lines: () -> TextLines,
        pageHeight: () -> Float,
        onCursorMoved: () -> Unit,
        zoom: (KeyZoom) -> Unit,
    ): Boolean {
        val current = state.selection
            return when (val command = event.toKeyboardCommand(keyBindings)) {
                null -> editable && event.insertTypedCharacter(state)

                is KeyboardCommand.Move -> {
                    val target = cursor.move(
                        text = state.rawText,
                        selection = current,
                        move = command.move,
                        extend = event.isShiftPressed,
                        lines = lines(),
                        pageHeight = pageHeight(),
                    )
                    onCursorMoved()
                    state.setSelection(target)
                    true
                }

                // a read-only editor has nothing to insert it into, so tab moves the focus
                KeyboardCommand.InsertTab -> {
                    if (editable) state.replaceSelection("\t")
                    editable
                }

                KeyboardCommand.InsertLine -> {
                    if (editable) state.replaceSelection("\n")
                    editable
                }

                // like in IntelliJ: the current line and a selection in it stay as they are
                KeyboardCommand.StartNewLine -> {
                    if (editable) {
                        val line = state.lineOf(current.end)
                        val end = state.lineEnd(line)
                        state.localEdit(end, end, "\n" + indentOf(state.rawText, state.lineStart(line)))
                    }
                    editable
                }

                KeyboardCommand.Backspace -> {
                    if (editable && folds?.inline?.openInsteadOfDelete(backward = true) != true) {
                        deleteTo(state, previousCharacter(state.rawText, current.min))
                    }
                    editable
                }

                KeyboardCommand.Delete -> {
                    if (editable && folds?.inline?.openInsteadOfDelete(backward = false) != true) {
                        deleteTo(state, nextCharacter(state.rawText, current.max))
                    }
                    editable
                }

                KeyboardCommand.BackspaceWord -> {
                    if (editable && folds?.inline?.openInsteadOfDelete(backward = true) != true) {
                        deleteTo(state, previousWord(state.rawText, current.min))
                    }
                    editable
                }

                KeyboardCommand.DeleteWord -> {
                    if (editable && folds?.inline?.openInsteadOfDelete(backward = false) != true) {
                        deleteTo(state, nextWord(state.rawText, current.max))
                    }
                    editable
                }

                KeyboardCommand.Copy -> {
                    if (!current.collapsed) menu.copy()
                    true
                }

                KeyboardCommand.Cut -> {
                    if (editable && !current.collapsed) menu.cut()
                    editable
                }

                KeyboardCommand.Paste -> {
                    if (editable) menu.paste()
                    editable
                }

                KeyboardCommand.SelectAll -> {
                    menu.selectAll()
                    true
                }

                KeyboardCommand.Undo -> {
                    if (editable) state.undo()
                    editable
                }

                KeyboardCommand.Redo -> {
                    if (editable) state.redo()
                    editable
                }

                KeyboardCommand.ZoomIn -> {
                    // the keys of IntelliJ, where there is nothing to fold they zoom
                    if (folds?.expandAtCursor() != true) zoom(KeyZoom.In)
                    true
                }

                KeyboardCommand.ZoomOut -> {
                    if (folds?.collapseAtCursor() != true) zoom(KeyZoom.Out)
                    true
                }

                KeyboardCommand.ZoomReset -> {
                    zoom(KeyZoom.Reset)
                    true
                }
            }
    }
}

/** removes the selection, or without one the text up to [target] from the cursor */
private fun deleteTo(state: KodeEditorState, target: Int) {
    val selection = state.selection
    when {
        !selection.collapsed -> state.replaceSelection("")
        target < selection.min -> state.localEdit(target, selection.min, "")
        target > selection.max -> state.localEdit(selection.max, target, "")
    }
}

/** types the character of a key of a hardware keyboard */
private fun KeyEvent.insertTypedCharacter(state: KodeEditorState): Boolean {
    val code = utf16CodePoint
    val isAccentOnly = code and KeyCharacterMap.COMBINING_ACCENT != 0
    if (code == 0 || isAccentOnly || Character.isISOControl(code) || isCtrlPressed || isMetaPressed) return false
    state.replaceSelection(String(Character.toChars(code)))
    return true
}

/**
 * The cursor of the editor. It has its own small layer, so blinking only changes the alpha of that layer.
 */
