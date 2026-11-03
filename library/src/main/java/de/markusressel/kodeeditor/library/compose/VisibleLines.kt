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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * Draws that are not part of the text, and know nothing about being faded. What has the same alpha goes through one
 * layer, so a whole fold that fades costs about as much as a single line.
 */
internal class FadingDraws {
    private var groups: HashMap<Float, MutableList<() -> Unit>>? = null

    /**
     * @param alpha how visible this should be
     * @param draw what to draw
     */
    fun add(alpha: Float, draw: () -> Unit) {
        val current = groups ?: HashMap<Float, MutableList<() -> Unit>>().also { groups = it }
        current.getOrPut(alpha) { mutableListOf() } += draw
    }

    fun DrawScope.drawAll() {
        groups?.forEach { (alpha, draws) ->
            drawContext.canvas.saveLayer(size.toRect(), Paint().also { it.alpha = alpha })
            draws.forEach { it() }
            drawContext.canvas.restore()
        }
    }
}

/**
 * Draws the lines of [layout] that are within [window]. Only these lines are recorded, in a layer of their own, so a
 * moving cursor or selection records no text at all.
 */
@Composable
internal fun VisibleLines(
    layout: EditorLineLayout,
    window: State<IntRect>,
    decorations: List<KodeEditorLineDecoration> = emptyList(),
) {
    LaunchedEffect(layout) {
        snapshotFlow { layout.currentWrapWidth to layout.foldAnimations }.collectLatest {
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
                val left = -area.left.toFloat()
                val top = -area.top.toFloat()
                // alpha of drawText does not reach the colors of the highlighting, a layer does
                val fading = FadingDraws()
                translate(left, top) {
                    layout.linesWithin(area).forEach { line ->
                        val parts = layout.movingParts(line)
                        if (parts == null) {
                            drawText(textLayoutResult = line.layout, topLeft = line.position)
                        } else {
                            val drawParts = {
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
                            val alpha = layout.rowAlpha(line.row)
                            if (alpha >= 1f) drawParts() else fading.add(alpha) { translate(left, top) { drawParts() } }
                        }
                    }
                    layout.shownLeavingRows.forEach { row ->
                        val position = Offset(0f, row.baseline - row.layout.firstBaseline)
                        fading.add(row.alpha) {
                            translate(left, top) { drawText(textLayoutResult = row.layout, topLeft = position) }
                        }
                    }
                    layout.trailingTextsWithin(area, decorations).forEach { text ->
                        drawText(textLayoutResult = text.layout, topLeft = text.position)
                    }
                }
                with(fading) { drawAll() }
            },
    )
}

/**
 * Draws the backgrounds of the [decorations] in a layer of its own, below the selection and the text.
 *
 * @param layout where the lines are
 * @param decorations what to draw
 * @param window the part of the document that is drawn
 * @param textPadding the padding in front of the text, which the backgrounds cover too
 */
@Composable
internal fun LineDecorationLayer(
    layout: EditorLineLayout,
    decorations: List<KodeEditorLineDecoration>,
    window: State<IntRect>,
    textPadding: Float,
) {
    Box(
        modifier = Modifier
            .placedAt { window.value }
            .graphicsLayer {}
            .drawBehind {
                val area = window.value
                val first = layout.getLineForVerticalPosition(area.top.toFloat())
                val last = layout.getLineForVerticalPosition(area.bottom.toFloat())
                val fading = FadingDraws()

                fun drawLine(line: Int, top: Float, height: Float, alpha: Float) {
                    val decorated = DecoratedLine(
                        line = line,
                        width = area.width + textPadding,
                        height = height,
                        textStart = textPadding - area.left,
                    )
                    val draw = {
                        translate(-textPadding, top - area.top) {
                            decorations.forEach { decoration -> with(decoration) { drawBackground(decorated) } }
                        }
                    }
                    if (alpha >= 1f) draw() else fading.add(alpha, draw)
                }

                for (line in first..last) {
                    if (layout.isLineHidden(line)) continue
                    val top = layout.getLineTop(line)
                    val height = layout.getLineBottom(line) - top
                    drawLine(line, top + layout.shownLineDistance(line), height, layout.lineAlpha(line))
                }
                layout.shownLeavingRows.forEach { row -> drawLine(row.line, row.top, layout.lineHeight, row.alpha) }
                with(fading) { drawAll() }
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
