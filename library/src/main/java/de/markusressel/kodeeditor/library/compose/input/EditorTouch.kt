package de.markusressel.kodeeditor.library.compose.input

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.cursor.HandleKind
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState

/** What the fingers, a mouse and the selection handles do to the editor right now */
@Stable
internal class EditorTouch {
    /** whether the last input was a touch, the handles are only there for touch input */
    var touchInput by mutableStateOf(false)

    /** a long press on nothing to select asks for the menu at the cursor */
    var cursorMenuRequested by mutableStateOf(false)
    var draggedHandle by mutableStateOf<HandleKind?>(null)

    /** where the dragged handle or the finger that selects is, in the coordinates of the editor */
    var dragTarget by mutableStateOf<Offset?>(null)

    /** the word a long press selected, dragging extends the selection from it */
    var pressedWord by mutableStateOf<TextRange?>(null)
    var flinging by mutableStateOf(false)

    /** whether the touch that is down stopped a fling, then it does nothing else */
    var stoppedFling by mutableStateOf(false)
}

/**
 * What taps, double taps and long presses on the editor do: place the cursor, open folds, select words.
 *
 * @param state the text
 * @param layout the layout of the text
 * @param enabled whether the editor is enabled
 * @param viewState stops what it animates when a finger comes down
 * @param touch the state of the touch, which this changes
 * @param haptics gives feedback for a long press
 * @param focus focuses the editor and shows the keyboard
 * @param toText the text position of a point of the editor
 * @param tapGutter lets the bar take a tap, true if it did
 * @param tapTrailingText lets what is shown after the text of a line take a tap at a text position, true if it did
 */
internal class EditorTouchGestures(
    private val state: KodeEditorState,
    private val layout: EditorLineLayout,
    private val enabled: Boolean,
    private val viewState: KodeEditorViewState,
    private val touch: EditorTouch,
    private val haptics: HapticFeedback,
    private val focus: () -> Unit,
    private val toText: (Offset) -> Offset,
    private val tapGutter: (Offset) -> Boolean,
    private val tapTrailingText: (Offset) -> Boolean,
) : EditorGestureHandler {

    private fun offsetAt(position: Offset) = layout.getOffsetForPosition(toText(position))

    override fun onDown(type: PointerType) {
        viewState.interrupt()
        touch.touchInput = type == PointerType.Touch
        touch.cursorMenuRequested = false
        touch.stoppedFling = touch.flinging
    }

    override fun onTap(position: Offset) {
        // this touch only stops the fling
        if (!enabled || touch.stoppedFling || tapGutter(position)) return
        val point = toText(position)
        if (tapTrailingText(point)) return
        focus()
        val fold = layout.foldedRangeAt(point)
        val target = offsetAt(position)
        state.setSelection(TextRange(if (fold == null) target else target.coerceIn(fold.min, fold.max)))
        fold?.let(layout::openFold)
    }

    override fun onDoubleTap(position: Offset) {
        // two quick taps on a button of the bar are two taps
        if (!enabled || touch.stoppedFling || tapGutter(position)) return
        focus()
        state.setSelection(layout.getWordBoundary(offsetAt(position)))
    }

    override fun onSelectionStart(position: Offset, type: PointerType) {
        if (!enabled) return
        focus()
        val target = offsetAt(position)
        val word = layout.foldedRangeAt(toText(position))
            ?: if (type == PointerType.Mouse) TextRange(target) else layout.getWordBoundary(target)
        if (type != PointerType.Mouse) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        state.setSelection(word)
        // nothing to select here, so the menu offers what can be done at the cursor
        touch.cursorMenuRequested = word.collapsed && type != PointerType.Mouse
        touch.pressedWord = word
        touch.dragTarget = position
        touch.draggedHandle = HandleKind.SelectionEnd
    }

    override fun onSelectionDrag(position: Offset) {
        val word = touch.pressedWord ?: return
        touch.dragTarget = position
        val target = offsetAt(position)
        state.setSelection(TextRange(minOf(word.min, target), maxOf(word.max, target)))
    }

    override fun onSelectionEnd() {
        touch.pressedWord = null
        touch.dragTarget = null
        touch.draggedHandle = null
    }
}
