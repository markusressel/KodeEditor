package de.markusressel.kodeeditor.library.compose

import android.view.InputDevice
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.TextRange
import java.text.BreakIterator

/**
 * What a key press does in the editor.
 */
internal sealed interface KeyboardCommand {
    data class Move(val move: CursorMove) : KeyboardCommand
    data object InsertTab : KeyboardCommand
    data object InsertLine : KeyboardCommand
    data object Backspace : KeyboardCommand
    data object Delete : KeyboardCommand
    data object BackspaceWord : KeyboardCommand
    data object DeleteWord : KeyboardCommand
    data object Copy : KeyboardCommand
    data object Cut : KeyboardCommand
    data object Paste : KeyboardCommand
    data object SelectAll : KeyboardCommand
    data object Undo : KeyboardCommand
    data object Redo : KeyboardCommand
    data object ZoomIn : KeyboardCommand
    data object ZoomOut : KeyboardCommand
    data object ZoomReset : KeyboardCommand
}

internal enum class CursorMove {
    CharacterLeft, CharacterRight, WordLeft, WordRight, LineUp, LineDown, ParagraphUp, ParagraphDown, PageUp, PageDown,
    LineStart, LineEnd, TextStart, TextEnd,
}

internal fun keyboardCommandFor(
    key: Key,
    ctrl: Boolean,
    alt: Boolean,
    meta: Boolean,
    shift: Boolean = false,
): KeyboardCommand? {
    if (meta) return null
    // shift + tab stays with the focus system: it is the way out of the editor for a keyboard
    if (key == Key.Tab) return if (ctrl || alt || shift) null else KeyboardCommand.InsertTab
    val jump = ctrl || alt
    val move = when (key) {
        Key.DirectionLeft -> if (jump) CursorMove.WordLeft else CursorMove.CharacterLeft
        Key.DirectionRight -> if (jump) CursorMove.WordRight else CursorMove.CharacterRight
        Key.DirectionUp -> if (jump) CursorMove.ParagraphUp else CursorMove.LineUp
        Key.DirectionDown -> if (jump) CursorMove.ParagraphDown else CursorMove.LineDown
        Key.PageUp -> if (jump) null else CursorMove.PageUp
        Key.PageDown -> if (jump) null else CursorMove.PageDown
        Key.MoveHome -> if (ctrl) CursorMove.TextStart else CursorMove.LineStart
        Key.MoveEnd -> if (ctrl) CursorMove.TextEnd else CursorMove.LineEnd
        else -> null
    }
    if (move != null) return KeyboardCommand.Move(move)
    if (!ctrl && !alt) {
        when (key) {
            Key.Enter, Key.NumPadEnter -> return KeyboardCommand.InsertLine
            Key.Backspace -> return KeyboardCommand.Backspace
            Key.Delete -> return KeyboardCommand.Delete
        }
    }
    if (!ctrl || alt) return null
    return when (key) {
        Key.Backspace -> KeyboardCommand.BackspaceWord
        Key.Delete -> KeyboardCommand.DeleteWord
        Key.A -> KeyboardCommand.SelectAll
        Key.C -> KeyboardCommand.Copy
        Key.X -> KeyboardCommand.Cut
        Key.V -> KeyboardCommand.Paste
        Key.Z -> if (shift) KeyboardCommand.Redo else KeyboardCommand.Undo
        Key.Y -> KeyboardCommand.Redo
        Key.Plus, Key.Equals, Key.NumPadAdd -> KeyboardCommand.ZoomIn
        Key.Minus, Key.NumPadSubtract -> KeyboardCommand.ZoomOut
        Key.Zero, Key.NumPad0 -> KeyboardCommand.ZoomReset
        else -> null
    }
}

internal fun KeyEvent.toKeyboardCommand(): KeyboardCommand? =
    keyboardCommandFor(key, isCtrlPressed, isAltPressed, isMetaPressed, isShiftPressed)

/**
 * A keyboard to write with, unlike the D-pad of a TV remote or a gamepad, which should keep moving the focus
 * between the elements of the screen.
 */
internal fun KeyEvent.isFromTextKeyboard(): Boolean {
    val device = nativeKeyEvent.device ?: return true
    return device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
}

/**
 * The lines of the laid out text, as far as moving the cursor needs them
 */
internal interface TextLines {
    val lineCount: Int
    fun lineOf(offset: Int): Int
    fun lineAt(y: Float): Int
    fun topOf(line: Int): Float
    fun xOf(offset: Int): Float
    fun offsetAt(line: Int, x: Float): Int
}

/**
 * Moves the cursor for the arrow and page keys.
 */
internal class KeyboardCursor {

    /** the column that moving up and down keeps, also across shorter lines */
    private var column: Float? = null
    private var movedTo: Int? = null

    /**
     * @param text the text of the editor
     * @param selection the current selection, its end is the cursor
     * @param extend true to extend the selection (shift), false to collapse it
     * @param pageHeight the height of one page, in the coordinates of [lines]
     * @return the new selection
     */
    fun move(
        text: String,
        selection: TextRange,
        move: CursorMove,
        extend: Boolean,
        lines: TextLines,
        pageHeight: Float,
    ): TextRange {
        if (movedTo != selection.end) column = null

        val from = when {
            extend || selection.collapsed -> selection.end
            move in BACKWARD -> selection.min
            else -> selection.max
        }
        val target = when (move) {
            // the arrow keys collapse a selection to its side, without moving further
            CursorMove.CharacterLeft ->
                if (extend || selection.collapsed) previousCharacter(text, from) else from

            CursorMove.CharacterRight ->
                if (extend || selection.collapsed) nextCharacter(text, from) else from

            CursorMove.WordLeft -> previousWord(text, from)
            CursorMove.WordRight -> nextWord(text, from)
            CursorMove.ParagraphUp -> previousParagraph(text, from)
            CursorMove.ParagraphDown -> nextParagraph(text, from)
            CursorMove.LineUp -> vertical(text, from, lines, lines.lineOf(from) - 1)
            CursorMove.LineDown -> vertical(text, from, lines, lines.lineOf(from) + 1)
            CursorMove.PageUp -> vertical(text, from, lines, lines.lineAt(topOf(lines, from) - pageHeight))
            CursorMove.PageDown -> vertical(text, from, lines, lines.lineAt(topOf(lines, from) + pageHeight))
            CursorMove.LineStart -> text.lastIndexOf('\n', from - 1) + 1
            CursorMove.LineEnd -> text.indexOf('\n', from).let { if (it < 0) text.length else it }
            CursorMove.TextStart -> 0
            CursorMove.TextEnd -> text.length
        }.coerceIn(0, text.length)

        if (move !in VERTICAL) column = null
        movedTo = target
        return if (extend) TextRange(selection.start, target) else TextRange(target)
    }

    private fun topOf(lines: TextLines, offset: Int) = lines.topOf(lines.lineOf(offset))

    private fun vertical(text: String, from: Int, lines: TextLines, line: Int): Int {
        val x = column ?: lines.xOf(from).also { column = it }
        return when {
            line < 0 -> 0
            line >= lines.lineCount -> text.length
            else -> lines.offsetAt(line, x)
        }
    }

    private companion object {
        val BACKWARD = setOf(
            CursorMove.CharacterLeft,
            CursorMove.WordLeft,
            CursorMove.LineUp,
            CursorMove.ParagraphUp,
            CursorMove.PageUp,
            CursorMove.LineStart,
            CursorMove.TextStart,
        )
        val VERTICAL = setOf(CursorMove.LineUp, CursorMove.LineDown, CursorMove.PageUp, CursorMove.PageDown)
    }
}

/** the character before [offset], a whole emoji or a letter with its combining marks */
internal fun previousCharacter(text: String, offset: Int): Int = characters(text).preceding(offset).let {
    if (it == BreakIterator.DONE) 0 else it
}

internal fun nextCharacter(text: String, offset: Int): Int = characters(text).following(offset).let {
    if (it == BreakIterator.DONE) text.length else it
}

private fun characters(text: String) = BreakIterator.getCharacterInstance().apply { setText(text) }

internal fun Char.isWordPart() = isLetterOrDigit() || this == '_' || isSurrogate() ||
    category == CharCategory.NON_SPACING_MARK

/** the start of the word before [offset] */
internal fun previousWord(text: String, offset: Int): Int {
    var index = offset
    while (index > 0 && !text[index - 1].isWordPart()) index--
    while (index > 0 && text[index - 1].isWordPart()) index--
    return index
}

/** the end of the word after [offset] */
internal fun nextWord(text: String, offset: Int): Int {
    var index = offset
    while (index < text.length && !text[index].isWordPart()) index++
    while (index < text.length && text[index].isWordPart()) index++
    return index
}

/** the start of the paragraph, or of the one before when already there */
internal fun previousParagraph(text: String, offset: Int): Int {
    if (offset == 0) return 0
    return text.lastIndexOf('\n', offset - 2).let { if (offset < 2) 0 else it + 1 }
}

/** the end of the paragraph, or of the one after when already there */
internal fun nextParagraph(text: String, offset: Int): Int {
    if (offset >= text.length) return text.length
    return text.indexOf('\n', offset + 1).let { if (it < 0) text.length else it }
}
