package de.markusressel.kodeeditor.library.compose.gutter

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntRect
import de.markusressel.kodeeditor.library.compose.gutter.DecoratedLine
import de.markusressel.kodeeditor.library.compose.gutter.KodeEditorLineDecoration
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.layout.FadingDraws
import de.markusressel.kodeeditor.library.compose.layout.placedAt

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
