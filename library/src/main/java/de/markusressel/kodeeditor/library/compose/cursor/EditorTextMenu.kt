package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.input.EditorTouch
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import kotlinx.coroutines.delay

/** how long the selection has to stay before its menu shows up */
private const val MENU_DELAY_MS = 250L

/**
 * The actions of the text menu.
 */
internal class HandleMenu(
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

/**
 * Shows the text menu of the system above the selection, after a touch selected something or asked for the menu at
 * the cursor. It hides while the selection or the view moves and shows up again when it stopped.
 *
 * @param state the text
 * @param layout the layout of the text
 * @param menu what the menu does
 * @param readOnly whether the menu must not change the text
 * @param focused whether the editor has the focus
 * @param touch whether the last input was a touch and what it asked for
 * @param hasClipboardText whether there is text to paste
 * @param coordinates the coordinates of the editor
 * @param toEditor the point of the editor for a point of the text
 * @param view where the view is, the menu hides while it changes
 */
@Composable
internal fun EditorTextMenuEffect(
    state: KodeEditorState,
    layout: EditorLineLayout,
    menu: HandleMenu,
    readOnly: Boolean,
    focused: Boolean,
    touch: EditorTouch,
    hasClipboardText: () -> Boolean,
    coordinates: () -> LayoutCoordinates?,
    toEditor: (Offset) -> Offset,
    view: Any,
) {
    val textToolbar = LocalTextToolbar.current

    fun showMenu() {
        val current = state.selection
        val attached = coordinates()?.takeIf { it.isAttached } ?: return
        val startRect = layout.getCursorRect(current.min)
        val endRect = layout.getCursorRect(current.max)
        val top = attached.localToRoot(toEditor(startRect.topLeft))
        val bottom = attached.localToRoot(toEditor(endRect.bottomRight))
        val hasSelection = !current.collapsed
        textToolbar.showMenu(
            rect = Rect(
                left = minOf(top.x, bottom.x),
                top = top.y,
                right = maxOf(top.x, bottom.x),
                bottom = bottom.y,
            ),
            onCopyRequested = menu::copy.takeIf { hasSelection },
            onPasteRequested = menu::paste.takeIf { !readOnly && hasClipboardText() },
            onCutRequested = menu::cut.takeIf { !readOnly && hasSelection },
            onSelectAllRequested = menu::selectAll.takeIf { current.length < state.rawText.length },
        )
    }

    val selection = state.selection
    LaunchedEffect(selection, focused, touch.touchInput, touch.cursorMenuRequested, touch.draggedHandle, view) {
        val wanted = focused && touch.touchInput && touch.draggedHandle == null &&
            (!selection.collapsed || touch.cursorMenuRequested)
        if (textToolbar.status == TextToolbarStatus.Shown) textToolbar.hide()
        if (wanted) {
            delay(MENU_DELAY_MS)
            showMenu()
        }
    }
}
