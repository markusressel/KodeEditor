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
import kotlin.math.abs

/** how long the selection has to stay before its menu shows up */
private const val MENU_DELAY_MS = 250L

/**
 * how long a selection that others move has to stay before its menu moves with it. The system hides a menu while it
 * moves and shows it 50 ms after its last move, so a menu that follows each change of an agent would never show
 */
private const val MENU_MOVE_DELAY_MS = 300L

/**
 * Shows the text menu of the system above the selection, after a touch selected something or asked for the menu at
 * the cursor. It hides while the user moves the selection or the view, and shows up again when they stopped. When
 * others change the text, e.g. an agent that writes, the menu stays open where it is, so the user can still use it, and
 * moves to the selection once it rested for [MENU_MOVE_DELAY_MS] and moved by half a line or more.
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
 * @param offset where the view is, the menu moves with it
 * @param zoom the zoom of the view, the menu moves with it
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
    offset: Offset,
    zoom: Float,
    userMovingView: Boolean,
) {
    val textToolbar = LocalTextToolbar.current
    // what the menu was shown with last, it is only shown again when that changes
    val shown = remember { arrayOfNulls<Any>(1) }

    /** @param moving whether the menu is open and only moves, it stays for a move of less than half a line */
    fun showMenu(moving: Boolean = false) {
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
        // a menu that is shown with the same place and items is left alone, showing it again redraws it
        val content = listOf(rect, hasSelection, canPaste, canSelectAll)
        val open = textToolbar.status == TextToolbarStatus.Shown
        if (open && content == shown[0]) return
        val before = (shown[0] as? List<*>)?.firstOrNull() as? Rect
        val near = layout.lineHeight / 2 * zoom
        if (moving && open && before != null && content.drop(1) == (shown[0] as List<*>).drop(1) &&
            abs(before.left - rect.left) < near && abs(before.top - rect.top) < near &&
            abs(before.bottom - rect.bottom) < near
        ) {
            return
        }
        shown[0] = content
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
    // others change the text, or the view keeps its place for that: the menu stays open where it is, and moves to the
    // selection once that rests. The system hides a menu while it moves, so it would not show while an agent writes
    LaunchedEffect(selection, offset, zoom) {
        if (!wanted || textToolbar.status != TextToolbarStatus.Shown) return@LaunchedEffect
        delay(MENU_MOVE_DELAY_MS)
        if (textToolbar.status == TextToolbarStatus.Shown) showMenu(moving = true)
    }
}
