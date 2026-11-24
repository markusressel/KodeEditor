package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntRect

/**
 * Draws the selection in a layer of its own.
 *
 * @param layout where the text is
 * @param selection the current selection, null to show none
 * @param window the part of the document that is drawn
 */
@Composable
internal fun SelectionLayer(layout: EditorLineLayout, selection: () -> TextRange?, window: State<IntRect>) {
    val color = LocalTextSelectionColors.current.backgroundColor
    Box(
        modifier = Modifier
            .placedAt { window.value }
            .graphicsLayer {}
            .drawBehind {
                val range = selection()?.takeIf { !it.collapsed } ?: return@drawBehind
                val area = window.value
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    drawPath(layout.shownPathForRange(range.min, range.max), color)
                }
            },
    )
}
