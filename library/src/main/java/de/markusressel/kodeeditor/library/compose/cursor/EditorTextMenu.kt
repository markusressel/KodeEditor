package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import de.markusressel.kodeeditor.library.compose.EditorTouch
import de.markusressel.kodeeditor.library.compose.input.EditorClipboardActions
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import kotlinx.coroutines.delay

/** how long the selection has to stay before its menu shows up */
private const val MENU_DELAY_MS = 250L

/**
 * Shows the text menu of the system above the selection, after a touch selected something or asked for the menu at
 * the cursor. It hides while the user moves the selection or the view, and shows up again when they stopped. When
 * others change the text, e.g. an agent that writes, the menu stays open where it is, so the user can use it: they know
 * what they selected. The system hides a menu while it moves, so one that followed each change would not show.
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
 * @param userMovingView whether the user moves the view, the menu hides meanwhile
 */
@Composable
internal fun EditorTextMenuEffect(
    state: KodeEditorState,
    layout: EditorLineLayout,
    menu: EditorClipboardActions,
    readOnly: Boolean,
    focused: Boolean,
    touch: EditorTouch,
    hasClipboardText: () -> Boolean,
    coordinates: () -> LayoutCoordinates?,
    toEditor: (Offset) -> Offset,
    userMovingView: Boolean,
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
        val rect = Rect(
            left = minOf(top.x, bottom.x),
            top = top.y,
            right = maxOf(top.x, bottom.x),
            bottom = bottom.y,
        )
        val canPaste = !readOnly && hasClipboardText()
        val canSelectAll = current.length < state.rawText.length
        textToolbar.showMenu(
            rect = rect,
            onCopyRequested = menu::copy.takeIf { hasSelection },
            onPasteRequested = menu::paste.takeIf { canPaste },
            onCutRequested = menu::cut.takeIf { !readOnly && hasSelection },
            onSelectAllRequested = menu::selectAll.takeIf { canSelectAll },
        )
    }

    val selection = state.selection
    // the selection the user made last: one that only others moved, by changing the text in front of it, keeps it
    val userSelection = remember { arrayOf(selection) }
    if (selection != state.selectionMovedByOthers) userSelection[0] = selection
    val wanted = focused && touch.touchInput && touch.draggedHandle == null && !userMovingView &&
        (!selection.collapsed || touch.cursorMenuRequested)
    LaunchedEffect(userSelection[0], wanted) {
        if (textToolbar.status == TextToolbarStatus.Shown) textToolbar.hide()
        if (wanted) {
            delay(MENU_DELAY_MS)
            showMenu()
        }
    }
}
