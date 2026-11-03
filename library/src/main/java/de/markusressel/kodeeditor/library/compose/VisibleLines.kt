package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.flow.collectLatest

/** enough single line layouts for the lines of a whole window at the lowest zoom, so unchanged lines are reused */
internal const val LINE_LAYOUT_CACHE_SIZE = 2048

/**
 * Places the content at the rectangle given by [rect] and takes no space in the parent, so it works at any size of
 * the document. Reading the rectangle only affects the placement, nothing recomposes when it changes.
 */
internal fun Modifier.placedAt(rect: () -> IntRect) = layout { measurable, constraints ->
    val target = rect()
    val placeable = measurable.measure(Constraints.fixed(target.width, target.height))
    layout(constraints.minWidth, constraints.minHeight) {
        placeable.place(target.left, target.top)
    }
}

/**
 * Draws the lines of [layout] that are within [window]. Only these lines are recorded, in a layer of their own, so a
 * moving cursor or selection records no text at all.
 */
@Composable
internal fun VisibleLines(layout: EditorLineLayout, window: State<IntRect>) {
    LaunchedEffect(layout) {
        snapshotFlow { layout.currentWrapWidth }.collectLatest {
            while (layout.reflowing) {
                withFrameMillis(layout::onReflowFrame)
            }
        }
    }

    Box(
        modifier = Modifier
            .placedAt { window.value }
            .graphicsLayer {}
            .drawBehind {
                val area = window.value
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    layout.linesWithin(area).forEach { line ->
                        val parts = layout.movingParts(line)
                        if (parts == null) {
                            drawText(textLayoutResult = line.layout, topLeft = line.position)
                        } else {
                            parts.forEach { part ->
                                translate(part.distance.x, part.distance.y) {
                                    clipRect(
                                        left = line.position.x + part.left,
                                        top = line.position.y,
                                        right = line.position.x + part.right,
                                        bottom = line.position.y + line.layout.size.height,
                                    ) {
                                        drawText(textLayoutResult = line.layout, topLeft = line.position)
                                    }
                                }
                            }
                        }
                    }
                }
            },
    )
}

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
