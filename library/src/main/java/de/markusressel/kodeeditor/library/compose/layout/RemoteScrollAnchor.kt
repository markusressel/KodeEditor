package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.ui.geometry.Offset
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
