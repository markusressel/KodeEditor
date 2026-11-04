package de.markusressel.kodeeditor.library.compose

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** how much text the keyboard gets at once, the binder can not carry a whole large document */
private const val MAX_TEXT_FOR_KEYBOARD = 16 * 1024

/** what the keyboard can ask the editor for, other than changing the text */
internal interface KeyboardActions {
    fun onKeyEvent(event: KeyEvent): Boolean
    fun onContextMenuAction(id: Int): Boolean

    /** @return true if a delete in that direction would take hidden text, which is shown now and the delete is off */
    fun openFoldInsteadOfDelete(backward: Boolean): Boolean = false
}

/**
 * The connection of the keyboard to a [KodeEditorState].
 *
 * The system calls this on the main thread, so each call reads and changes the text directly, and no input can get
 * lost between the keyboard and the editor. It follows the behavior of [android.view.inputmethod.BaseInputConnection],
 * which the tests compare it with.
 */
internal class EditorInputConnection(
    private val state: KodeEditorState,
    private val actions: KeyboardActions,
) : InputConnection {

    private var batches = 0

    private val text get() = state.rawText

    override fun beginBatchEdit(): Boolean {
        batches++
        state.beginBatch()
        return true
    }

    override fun endBatchEdit(): Boolean {
        if (batches == 0) return false
        batches--
        state.endBatch()
        return batches > 0
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        val end = state.selection.min
        val start = (end - n.coerceAtMost(MAX_TEXT_FOR_KEYBOARD)).coerceAtLeast(0)
        return text.substring(start, end)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
        val start = state.selection.max
        return text.substring(start, (start + n.coerceAtMost(MAX_TEXT_FOR_KEYBOARD)).coerceAtMost(text.length))
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        val selection = state.selection
        if (selection.collapsed) return null
        return text.substring(selection.min, selection.max.coerceAtMost(selection.min + MAX_TEXT_FOR_KEYBOARD))
    }

    override fun getCursorCapsMode(reqModes: Int): Int {
        return TextUtils.getCapsMode(text, state.selection.min, reqModes)
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        if (request != null && flags and InputConnection.GET_EXTRACTED_TEXT_MONITOR != 0) {
            state.inputLink?.monitorExtractedText(request.token)
        }
        return state.extractedText()
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        delete(beforeLength, afterLength)
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        val (first, last) = rangeKeptOnDelete()
        var before = 0
        var position = first
        repeat(beforeLength) {
            if (position > 0) {
                val step = Character.charCount(text.codePointBefore(position))
                before += step
                position -= step
            }
        }
        var after = 0
        position = last
        repeat(afterLength) {
            if (position < text.length) {
                val step = Character.charCount(text.codePointAt(position))
                after += step
                position += step
            }
        }
        delete(before, after)
        return true
    }

    /** a delete counts from the outside of the selection and the composing text, it never removes those */
    private fun rangeKeptOnDelete(): Pair<Int, Int> {
        val selection = state.selection
        val composition = state.composition ?: return selection.min to selection.max
        return minOf(selection.min, composition.min) to maxOf(selection.max, composition.max)
    }

    private fun delete(beforeLength: Int, afterLength: Int) {
        val (first, last) = rangeKeptOnDelete()
        val cursor = state.selection.end
        val backward = beforeLength > 0 && first == cursor
        val forward = afterLength > 0 && last == cursor
        if ((backward && openFold(backward = true)) || (forward && openFold(backward = false))) return
        state.batch { removeAround(first, last, beforeLength, afterLength) }
    }

    /** the keyboard expects the text to have changed, so it has to look at it again when it did not */
    private fun openFold(backward: Boolean): Boolean {
        val opened = actions.openFoldInsteadOfDelete(backward)
        if (opened) state.inputLink?.restart()
        return opened
    }

    private fun removeAround(first: Int, last: Int, beforeLength: Int, afterLength: Int) {
        val afterEnd = (last.toLong() + afterLength.coerceAtLeast(0)).coerceAtMost(text.length.toLong()).toInt()
        if (afterEnd > last) removeKeepingRanges(last, afterEnd)
        val beforeStart = (first.toLong() - beforeLength.coerceAtLeast(0)).coerceAtLeast(0L).toInt()
        if (first > beforeStart) removeKeepingRanges(beforeStart, first)
    }

    private fun removeKeepingRanges(start: Int, end: Int) {
        val splice = TextSplice(start, end - start, "")
        state.editFromKeyboard(splice, state.selection.mapped(splice), state.composition?.mapped(splice))
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        replaceComposing(text.toString(), newCursorPosition, keepComposing = true)
        return true
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        replaceComposing(text.toString(), newCursorPosition, keepComposing = false)
        return true
    }

    /** a keyboard that marked the word at the cursor deletes by making that word shorter */
    private fun isDeleteAtEndOfComposing(replacement: String): Boolean {
        val composition = state.composition ?: return false
        val selection = state.selection
        return selection.collapsed && selection.end == composition.max && replacement.length < composition.length &&
            text.startsWith(replacement, composition.min)
    }

    private fun replaceComposing(replacement: String, newCursorPosition: Int, keepComposing: Boolean) {
        if (isDeleteAtEndOfComposing(replacement) && openFold(backward = true)) return
        val range = state.composition ?: state.selection
        val start = range.min
        val end = range.max
        val delta = replacement.length - (end - start)
        // like the framework: larger than zero is relative to the end of the new text, otherwise to its start, and a
        // negative value that is cut off at the insertion point ends up behind the inserted text
        val cursor = if (newCursorPosition > 0) {
            (end.toLong() + newCursorPosition - 1).coerceAtMost(text.length.toLong()).toInt() + delta
        } else {
            val position = (start.toLong() + newCursorPosition).coerceAtLeast(0L).toInt()
            if (newCursorPosition != 0 && position == start && start == end) start + replacement.length else position
        }
        val composition = if (keepComposing && replacement.isNotEmpty()) {
            TextRange(start, start + replacement.length)
        } else {
            null
        }
        state.editFromKeyboard(TextSplice(start, end - start, replacement), TextRange(cursor), composition)
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        val length = text.length
        val first = minOf(start, end).coerceIn(0, length)
        val last = maxOf(start, end).coerceIn(0, length)
        state.setSelectionFromKeyboard(state.selection, if (first == last) null else TextRange(first, last))
        return true
    }

    override fun finishComposingText(): Boolean {
        state.setSelectionFromKeyboard(state.selection, null)
        return true
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        val length = text.length
        // an invalid range is ignored, as in the framework
        if (start in 0..length && end in 0..length) {
            state.setSelectionFromKeyboard(TextRange(start, end), state.composition)
        }
        return true
    }

    override fun commitCompletion(text: CompletionInfo?): Boolean = false

    // the text is changed by other calls, this is only for showing the correction
    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true

    // there is no action in a multi line editor, like a text view this makes a keyboard's enter key start a new line
    override fun performEditorAction(editorAction: Int): Boolean {
        commitText("\n", 1)
        return true
    }

    override fun performContextMenuAction(id: Int): Boolean = actions.onContextMenuAction(id)

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        // an old way of keyboards to send text that no key stands for
        @Suppress("DEPRECATION")
        if (event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return commitText(event.characters.orEmpty(), 1)
        }
        return actions.onKeyEvent(event)
    }

    override fun clearMetaKeyStates(states: Int): Boolean = false

    override fun reportFullscreenMode(enabled: Boolean): Boolean = false

    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

    override fun getHandler(): Handler? = null

    override fun closeConnection() {
        while (batches > 0) endBatchEdit()
        finishComposingText()
    }

    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
}

/** the text around the selection, the binder can not carry all of it */
private fun KodeEditorState.extractedText(): ExtractedText {
    val text = rawText
    val range = selection
    val start = (range.min - MAX_TEXT_FOR_KEYBOARD / 2).coerceAtLeast(0)
    val end = (start + MAX_TEXT_FOR_KEYBOARD).coerceAtMost(text.length)
    return ExtractedText().apply {
        this.text = text.substring(start, end)
        startOffset = start
        partialStartOffset = -1
        partialEndOffset = -1
        selectionStart = (range.start - start).coerceAtMost(end - start)
        selectionEnd = (range.end - start).coerceAtMost(end - start)
    }
}

/**
 * Tells the keyboard about changes of the state that it needs to know about.
 */
internal class ViewInputLink(
    private val view: View,
    private val state: KodeEditorState,
) : InputLink {

    private var extractedTextToken: Int? = null

    private val manager: InputMethodManager
        get() = view.context.getSystemService(InputMethodManager::class.java)

    override fun update() {
        val selection = state.selection
        val composition = state.composition
        manager.updateSelection(view, selection.start, selection.end, composition?.min ?: -1, composition?.max ?: -1)
        extractedTextToken?.let { manager.updateExtractedText(view, it, state.extractedText()) }
    }

    override fun restart() {
        extractedTextToken = null
        manager.restartInput(view)
    }

    override fun monitorExtractedText(token: Int) {
        extractedTextToken = token
    }
}

/**
 * What the keyboard should offer for the editor
 */
internal fun EditorInfo.configureForEditor(state: KodeEditorState, autoCorrect: Boolean) {
    inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
        if (autoCorrect) InputType.TYPE_TEXT_FLAG_AUTO_CORRECT else 0
    // enter makes a new line, and a keyboard in landscape does not hide the editor
    imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or
        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
        EditorInfo.IME_FLAG_NO_FULLSCREEN
    val selection = state.selection
    initialSelStart = selection.start
    initialSelEnd = selection.end
    val text = state.rawText
    initialCapsMode = TextUtils.getCapsMode(text, selection.min, inputType)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val start = (selection.min - MAX_TEXT_FOR_KEYBOARD / 16).coerceAtLeast(0)
        val end = (selection.max + MAX_TEXT_FOR_KEYBOARD / 16).coerceAtMost(text.length)
        setInitialSurroundingSubText(text.substring(start, end), start)
    }
}

/**
 * Connects the keyboard to the state while this has the focus. Goes in front of the node that takes the focus.
 *
 * @param state the text the keyboard changes
 * @param active false if the text can not be changed, the keyboard is not needed then
 * @param autoCorrect whether the keyboard may correct words on its own
 * @param actions what the keyboard can ask for other than changing the text
 */
internal fun Modifier.editorInput(
    state: KodeEditorState,
    active: Boolean,
    autoCorrect: Boolean,
    actions: KeyboardActions,
) = this then EditorInputElement(state, active, autoCorrect, actions)

private data class EditorInputElement(
    val state: KodeEditorState,
    val active: Boolean,
    val autoCorrect: Boolean,
    val actions: KeyboardActions,
) : ModifierNodeElement<EditorInputNode>() {
    override fun create() = EditorInputNode(state, active, autoCorrect, actions)

    override fun update(node: EditorInputNode) = node.update(state, active, autoCorrect, actions)

    override fun InspectorInfo.inspectableProperties() {
        name = "editorInput"
    }
}

private class EditorInputNode(
    private var state: KodeEditorState,
    private var active: Boolean,
    private var autoCorrect: Boolean,
    private var actions: KeyboardActions,
) : Modifier.Node(), FocusEventModifierNode, PlatformTextInputModifierNode {

    private var focused = false
    private var session: Job? = null

    override fun onFocusEvent(focusState: FocusState) {
        focused = focusState.isFocused
        refreshSession()
    }

    fun update(state: KodeEditorState, active: Boolean, autoCorrect: Boolean, actions: KeyboardActions) {
        val restart = state !== this.state || autoCorrect != this.autoCorrect
        this.state = state
        this.active = active
        this.autoCorrect = autoCorrect
        this.actions = actions
        if (restart) stopSession()
        refreshSession()
    }

    override fun onDetach() {
        session = null
    }

    private fun refreshSession() {
        if (!focused || !active) {
            stopSession()
        } else if (session == null && isAttached) {
            session = coroutineScope.launch {
                establishTextInputSession {
                    startInputMethod(
                        PlatformTextInputMethodRequest { attributes ->
                            attributes.configureForEditor(state, autoCorrect)
                            EditorInputConnection(state, actions)
                        },
                    )
                }
            }
        }
    }

    private fun stopSession() {
        session?.cancel()
        session = null
    }
}
