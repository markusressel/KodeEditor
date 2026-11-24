package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.state.mapPosition

/**
 * Where the user looks, the position in the text that a change of somebody else in front of it must not move on the
 * screen, see `KodeEditor(keepViewOnRemoteChanges)`.
 */
internal class RemoteScrollAnchor {
    /** the position the user looks at, null for none */
    var anchor: () -> Int? = { null }

    /** scrolls the view by a distance, and returns how far it scrolled */
    var shift: (Offset) -> Offset = { Offset.Zero }

    /** the [anchor] right before the change of somebody else that is being applied */
    private var beforeChange: Int? = null

    /** told right before a change of somebody else, while the text and the rows are still those from before */
    val beforeRemoteChange: () -> Unit = { beforeChange = anchor() }

    /**
     * @param splice a change of the text
     * @param remote whether it is a change of somebody else, which moves the view
     * @return where the anchor is after the change, if the change is in front of it, null if it does not move it
     */
    fun movedBy(splice: TextSplice, remote: Boolean): Int? {
        val anchor = if (remote) beforeChange else null
        beforeChange = null
        return anchor?.takeIf { it > splice.start && it >= splice.start + splice.removedLength }
            ?.let { mapPosition(it, splice) }
    }
}

/**
 * @param layout the layout of the editor
 * @param caret where the cursor of the user is
 * @param focused whether the editor has the focus
 * @param view the visible part of the document, in the coordinates of the text with its padding
 * @param textPadding the padding in front of the text
 * @return what the user looks at: the cursor while the editor has the focus and the cursor is in view, otherwise the
 * start of the first visible row
 */
internal fun remoteScrollAnchorIn(
    layout: EditorLineLayout,
    caret: Int,
    focused: Boolean,
    view: Rect,
    textPadding: Float,
): Int {
    val rect = layout.getCursorRect(caret).translate(textPadding, 0f)
    val inView = rect.top >= view.top && rect.bottom <= view.bottom && rect.left >= view.left && rect.left <= view.right
    return if (focused && inView) caret else layout.startOfRow(layout.rowAt(view.top))
}

/**
 * Keeps the view where the user looks when others change the text in front of it, while this is in the composition.
 *
 * @param layout the layout of the editor
 * @param enabled whether it does, see `KodeEditor(keepViewOnRemoteChanges)`
 * @param anchor what the user looks at, see [remoteScrollAnchorIn]
 * @param scrollBy scrolls the view by a distance, and returns how far it scrolled
 */
@Composable
internal fun RemoteScrollAnchorEffect(
    layout: EditorLineLayout,
    enabled: Boolean,
    anchor: () -> Int,
    scrollBy: (Offset) -> Offset,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentAnchor by rememberUpdatedState(anchor)
    val currentScrollBy by rememberUpdatedState(scrollBy)
    DisposableEffect(layout) {
        layout.scrollAnchor.anchor = { if (currentEnabled) currentAnchor() else null }
        layout.scrollAnchor.shift = { currentScrollBy(it) }
        onDispose {
            layout.scrollAnchor.anchor = { null }
            layout.scrollAnchor.shift = { Offset.Zero }
        }
    }
}
