package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import de.markusressel.kodeeditor.library.compose.layout.RemoteScrollAnchorEffect
import de.markusressel.kodeeditor.library.compose.layout.remoteScrollAnchorIn
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.view.CursorFollower
import de.markusressel.kodeeditor.library.compose.view.CursorVisibility

/**
 * Keeps the view on what the user looks at while the text, the room for it and the cursor change: when lines wrap
 * differently, when others change the text in front of it, when the text gets shorter, and while the user edits.
 *
 * @param view the view of the editor
 * @param state the text
 * @param wrapWidth the width lines wrap at
 * @param anchors what the user works on, the cursor and the moving end of a selection first
 * @param focused whether the editor has the focus, read when it is needed
 * @param keepViewOnRemoteChanges whether text that others change in front of the view moves the view with it
 * @param keepCursorVisible whether the view follows the cursor while the user edits
 * @param cursorFollower tells whether the view follows the cursor
 * @param size the size of the editor
 * @param zoom the zoom factor
 * @param handleDragged whether a selection handle is dragged, which scrolls the view itself
 */
@Composable
internal fun KeepViewEffects(
    view: EditorViewController,
    state: KodeEditorState,
    wrapWidth: Float,
    anchors: () -> List<Int>,
    focused: () -> Boolean,
    keepViewOnRemoteChanges: Boolean,
    keepCursorVisible: Boolean,
    cursorFollower: CursorFollower,
    size: IntSize,
    zoom: Float,
    handleDragged: () -> Boolean,
) {
    val selection = state.selection
    val isFocused = focused()

    // the cursor or the selection stays where it is when the lines wrap differently, otherwise the top of the view does
    LaunchedEffect(view.layout, wrapWidth) {
        view.layout.takePositionAfterResize(view.offset.y, anchors(), view.geometry.visibleSize.height)?.let { y ->
            val before = view.offset.y
            view.scrollTo(view.offset.copy(y = y))
            view.layout.onScrolledByReflow(view.offset.y - before)
        }
    }

    // text that others insert or remove in front of what the user looks at moves the view with it, in the same frame
    RemoteScrollAnchorEffect(
        layout = view.layout,
        enabled = keepViewOnRemoteChanges,
        anchor = {
            val visible = view.geometry.visibleSize
            val shown = Rect(view.offset, Size(visible.width.toFloat(), visible.height.toFloat()))
            remoteScrollAnchorIn(view.layout, state.selection.end, focused(), shown, view.textStartPadding)
        },
        scrollBy = view::scrollBy,
    )

    // the text can get shorter and the zoom can change, the view must not stay beyond the end of the text
    LaunchedEffect(view.maxOffset) {
        val before = view.offset.y
        view.scrollTo(view.offset)
        view.layout.onScrolledByReflow(view.offset.y - before)
    }

    // Follow the cursor while the user edits: when the selection moves or the editor changes its size.
    // Panning or remote changes of the text alone don't scroll, also not when they move the selection.
    LaunchedEffect(keepCursorVisible, isFocused, selection, size, zoom, wrapWidth) {
        val follow = cursorFollower.shouldFollow(selection, isFocused, selection == state.selectionMovedByOthers)
        // a dragged handle scrolls the view itself
        if (!keepCursorVisible || !follow || size == IntSize.Zero || handleDragged()) {
            return@LaunchedEffect
        }
        view.scrollTo(
            CursorVisibility.offsetFor(
                offset = view.offset,
                cursor = view.layout.getCursorRect(selection.end).translate(view.textStartPadding, 0f),
                viewport = view.geometry.visibleSize,
                maxOffset = view.maxOffset,
            ),
        )
    }
}
