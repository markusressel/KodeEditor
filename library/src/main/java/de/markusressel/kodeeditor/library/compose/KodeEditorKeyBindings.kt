package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.input.key.Key

/**
 * What a key of a hardware keyboard can do in a [KodeEditor].
 */
enum class KodeEditorKeyAction(internal val command: KeyboardCommand) {
    MoveCharacterLeft(KeyboardCommand.Move(CursorMove.CharacterLeft)),
    MoveCharacterRight(KeyboardCommand.Move(CursorMove.CharacterRight)),
    MoveWordLeft(KeyboardCommand.Move(CursorMove.WordLeft)),
    MoveWordRight(KeyboardCommand.Move(CursorMove.WordRight)),
    MoveLineUp(KeyboardCommand.Move(CursorMove.LineUp)),
    MoveLineDown(KeyboardCommand.Move(CursorMove.LineDown)),
    MoveParagraphUp(KeyboardCommand.Move(CursorMove.ParagraphUp)),
    MoveParagraphDown(KeyboardCommand.Move(CursorMove.ParagraphDown)),
    MovePageUp(KeyboardCommand.Move(CursorMove.PageUp)),
    MovePageDown(KeyboardCommand.Move(CursorMove.PageDown)),
    MoveLineStart(KeyboardCommand.Move(CursorMove.LineStart)),
    MoveLineEnd(KeyboardCommand.Move(CursorMove.LineEnd)),
    MoveTextStart(KeyboardCommand.Move(CursorMove.TextStart)),
    MoveTextEnd(KeyboardCommand.Move(CursorMove.TextEnd)),

    /** in a read-only editor the key is left to the focus system */
    InsertTab(KeyboardCommand.InsertTab),
    InsertLine(KeyboardCommand.InsertLine),

    /** starts a new line below the current one, without splitting it */
    StartNewLine(KeyboardCommand.StartNewLine),
    Backspace(KeyboardCommand.Backspace),
    Delete(KeyboardCommand.Delete),
    BackspaceWord(KeyboardCommand.BackspaceWord),
    DeleteWord(KeyboardCommand.DeleteWord),
    Copy(KeyboardCommand.Copy),
    Cut(KeyboardCommand.Cut),
    Paste(KeyboardCommand.Paste),
    SelectAll(KeyboardCommand.SelectAll),
    Undo(KeyboardCommand.Undo),
    Redo(KeyboardCommand.Redo),

    /** shows what is folded at the cursor, without anything folded there it zooms in */
    ExpandOrZoomIn(KeyboardCommand.ZoomIn),

    /** folds what is at the cursor, without anything to fold there it zooms out */
    CollapseOrZoomOut(KeyboardCommand.ZoomOut),
    ZoomReset(KeyboardCommand.ZoomReset),
}

/**
 * A key combination of a hardware keyboard and what it does. Combinations with the meta key are never used.
 *
 * @param key the key
 * @param action what the key does
 * @param ctrl whether ctrl has to be pressed
 * @param alt whether alt has to be pressed
 * @param shift whether shift has to be pressed, null for both. With shift, moving the cursor extends the selection
 */
data class KodeEditorKeyBinding(
    val key: Key,
    val action: KodeEditorKeyAction,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean? = null,
) {
    internal fun matches(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean) =
        key == this.key && ctrl == this.ctrl && alt == this.alt && (this.shift == null || shift == this.shift)
}

/** the key alone, with ctrl, with alt and with both */
private fun jump(key: Key, plain: KodeEditorKeyAction, jump: KodeEditorKeyAction) = listOf(
    KodeEditorKeyBinding(key, plain),
    KodeEditorKeyBinding(key, jump, ctrl = true),
    KodeEditorKeyBinding(key, jump, alt = true),
    KodeEditorKeyBinding(key, jump, ctrl = true, alt = true),
)

/** the key alone or with alt moves within the line, with ctrl within the text */
private fun lineOrText(key: Key, line: KodeEditorKeyAction, text: KodeEditorKeyAction) = listOf(
    KodeEditorKeyBinding(key, line),
    KodeEditorKeyBinding(key, line, alt = true),
    KodeEditorKeyBinding(key, text, ctrl = true),
    KodeEditorKeyBinding(key, text, ctrl = true, alt = true),
)

private fun ctrl(action: KodeEditorKeyAction, keys: List<Key>) =
    keys.map { KodeEditorKeyBinding(it, action, ctrl = true) }

internal val DEFAULT_KEY_BINDINGS: List<KodeEditorKeyBinding> = buildList {
    addAll(jump(Key.DirectionLeft, KodeEditorKeyAction.MoveCharacterLeft, KodeEditorKeyAction.MoveWordLeft))
    addAll(jump(Key.DirectionRight, KodeEditorKeyAction.MoveCharacterRight, KodeEditorKeyAction.MoveWordRight))
    addAll(jump(Key.DirectionUp, KodeEditorKeyAction.MoveLineUp, KodeEditorKeyAction.MoveParagraphUp))
    addAll(jump(Key.DirectionDown, KodeEditorKeyAction.MoveLineDown, KodeEditorKeyAction.MoveParagraphDown))
    add(KodeEditorKeyBinding(Key.PageUp, KodeEditorKeyAction.MovePageUp))
    add(KodeEditorKeyBinding(Key.PageDown, KodeEditorKeyAction.MovePageDown))
    addAll(lineOrText(Key.MoveHome, KodeEditorKeyAction.MoveLineStart, KodeEditorKeyAction.MoveTextStart))
    addAll(lineOrText(Key.MoveEnd, KodeEditorKeyAction.MoveLineEnd, KodeEditorKeyAction.MoveTextEnd))
    // shift + tab stays with the focus system: it is the way out of the editor for a keyboard
    add(KodeEditorKeyBinding(Key.Tab, KodeEditorKeyAction.InsertTab, shift = false))
    for (key in listOf(Key.Enter, Key.NumPadEnter)) {
        add(KodeEditorKeyBinding(key, KodeEditorKeyAction.InsertLine, shift = false))
        add(KodeEditorKeyBinding(key, KodeEditorKeyAction.StartNewLine, shift = true))
    }
    add(KodeEditorKeyBinding(Key.Backspace, KodeEditorKeyAction.Backspace))
    add(KodeEditorKeyBinding(Key.Delete, KodeEditorKeyAction.Delete))
    addAll(ctrl(KodeEditorKeyAction.BackspaceWord, listOf(Key.Backspace)))
    addAll(ctrl(KodeEditorKeyAction.DeleteWord, listOf(Key.Delete)))
    addAll(ctrl(KodeEditorKeyAction.SelectAll, listOf(Key.A)))
    addAll(ctrl(KodeEditorKeyAction.Copy, listOf(Key.C)))
    addAll(ctrl(KodeEditorKeyAction.Cut, listOf(Key.X)))
    addAll(ctrl(KodeEditorKeyAction.Paste, listOf(Key.V)))
    add(KodeEditorKeyBinding(Key.Z, KodeEditorKeyAction.Undo, ctrl = true, shift = false))
    add(KodeEditorKeyBinding(Key.Z, KodeEditorKeyAction.Redo, ctrl = true, shift = true))
    addAll(ctrl(KodeEditorKeyAction.Redo, listOf(Key.Y)))
    addAll(ctrl(KodeEditorKeyAction.ExpandOrZoomIn, listOf(Key.Plus, Key.Equals, Key.NumPadAdd)))
    addAll(ctrl(KodeEditorKeyAction.CollapseOrZoomOut, listOf(Key.Minus, Key.NumPadSubtract)))
    addAll(ctrl(KodeEditorKeyAction.ZoomReset, listOf(Key.Zero, Key.NumPad0)))
}
