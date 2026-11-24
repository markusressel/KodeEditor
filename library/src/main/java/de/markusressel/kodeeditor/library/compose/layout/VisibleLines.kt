package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntRect
import de.markusressel.kodeeditor.library.compose.gutter.KodeEditorLineDecoration
import kotlinx.coroutines.flow.collectLatest

/** enough single line layouts for the lines of a whole window at the lowest zoom, so unchanged lines are reused */
internal const val LINE_LAYOUT_CACHE_SIZE = 2048

/** the corner radius of the box behind a placeholder, as a part of the line height */
private const val FOLD_BOX_RADIUS = 0.2f

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
    foldBoxColor: Color = Color.Transparent,
    appearSlide: Float = 0f,
) {
    // saveLayer copies it, so one is enough for all layers
    val layerPaint = remember { Paint() }
    LaunchedEffect(layout) {
        snapshotFlow { layout.currentWrapWidth to layout.foldAnimations }.collectLatest {
            while (layout.reflowing) {
                withFrameMillis(layout::onReflowFrame)
            }
        }
    }
    // a word at the start of a wrapped row slides to its place once it does not fit behind the row above anymore
    LaunchedEffect(layout) {
        val carries = layout.wrapCarries
        // reads what the words depend on, so it looks again whenever the hidden or the appearing text changes
        snapshotFlow {
            carries.current()
            carries.releasing
        }.collectLatest { releasing ->
            while (releasing && carries.releasing) {
                withFrameMillis { now ->
                    carries.current()
                    carries.onFrame(now)
                }
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
                val boxRadius = CornerRadius(layout.lineHeight * FOLD_BOX_RADIUS)

                fun drawRowContent(line: EditorLineLayout.DrawnLine) {
                    line.boxes.forEach { box ->
                        drawRoundRect(foldBoxColor, box.topLeft, box.size, boxRadius)
                    }
                    drawText(textLayoutResult = line.layout, topLeft = line.position)
                }

                /** draws the parts with an alpha of their own in a small layer each, the rest of the row as usual */
                fun drawFadingRow(line: EditorLineLayout.DrawnLine, parts: List<FadingPart>) {
                    // only this row is drawn with these clips, so they can reach far above and below it
                    val height = line.layout.size.height
                    val top = line.position.y - height
                    val bottom = line.position.y + 2f * height
                    val rects = parts.map { Rect(line.position.x + it.left, top, line.position.x + it.right, bottom) }
                    val fadingArea = Path().apply { rects.forEach { addRect(it) } }
                    clipPath(fadingArea, ClipOp.Difference) { drawRowContent(line) }
                    if (appearSlide == 0f) {
                        // pieces one behind the other in one layer, with a gradient of their alphas from the middle of
                        // one to the middle of the next, so there is no edge between them
                        gradientRuns(parts).forEach { run ->
                            val rect = Rect(line.position.x + run.left, top, line.position.x + run.right, bottom)
                            layerPaint.alpha = if (run.stops == null) run.alpha else 1f
                            drawContext.canvas.saveLayer(rect, layerPaint)
                            clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                                drawRowContent(line)
                                val stops = run.stops ?: return@clipRect
                                val startX = line.position.x + stops.first().first
                                val endX = line.position.x + stops.last().first
                                val colorStops = stops.map { (x, alpha) ->
                                    (line.position.x + x - startX) / (endX - startX) to Color.Black.copy(alpha = alpha)
                                }.toTypedArray()
                                drawRect(
                                    brush = Brush.horizontalGradient(*colorStops, startX = startX, endX = endX),
                                    topLeft = rect.topLeft,
                                    size = rect.size,
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                            drawContext.canvas.restore()
                        }
                        return
                    }
                    parts.forEachIndexed { index, part ->
                        if (part.alpha <= 0f) return@forEachIndexed
                        val rect = rects[index]
                        layerPaint.alpha = part.alpha
                        drawContext.canvas.saveLayer(rect, layerPaint)
                        clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                            // rises into its place while it appears
                            translate(top = (1f - part.alpha) * appearSlide) { drawRowContent(line) }
                        }
                        drawContext.canvas.restore()
                    }
                }

                // where text is not drawn yet ahead of the cursors of others, the same for all rows
                val cuts = layout.appearing.caretCuts()
                translate(left, top) {
                    /** @param inRegion draws only a part of the row, also where it is drawn later in a layer */
                    fun drawLine(line: EditorLineLayout.DrawnLine, inRegion: (() -> Unit) -> Unit = { it() }) {
                        val parts = layout.movingParts(line)
                        if (parts == null) {
                            val fadingParts = layout.appearing.fadingParts(line, cuts)
                            inRegion { if (fadingParts == null) drawRowContent(line) else drawFadingRow(line, fadingParts) }
                        } else {
                            val rowAlpha = layout.rowAlpha(line.row)
                            // text that still appears keeps its alpha while it moves, or it flashes up for the move
                            val fadingParts = layout.appearing.fadingParts(line, cuts)
                            parts.forEach { part ->
                                val drawPart = {
                                    translate(part.distance.x, part.distance.y) {
                                        clipRect(
                                            left = line.position.x + part.left,
                                            top = line.position.y,
                                            right = line.position.x + part.right,
                                            bottom = line.position.y + line.layout.size.height,
                                        ) {
                                            if (fadingParts == null) {
                                                drawRowContent(line)
                                            } else {
                                                drawFadingRow(line, fadingParts)
                                            }
                                        }
                                    }
                                }
                                val alpha = rowAlpha * part.alpha
                                if (alpha >= 1f) {
                                    inRegion(drawPart)
                                } else {
                                    fading.add(alpha) { translate(left, top) { inRegion(drawPart) } }
                                }
                            }
                        }
                    }

                    layout.linesWithin(area).forEach { line ->
                        val carried = layout.carriedPart(line.row)
                        if (carried == null) {
                            drawLine(line)
                            return@forEach
                        }
                        val rowTop = line.position.y
                        val rowBottom = rowTop + line.layout.size.height
                        // the word at the end of the row above, the rest of the row at its place
                        val wordLeft = line.position.x + carried.left
                        val wordRight = line.position.x + carried.right
                        drawLine(line) { draw ->
                            translate(carried.distance.x, carried.distance.y) {
                                clipRect(wordLeft, rowTop, wordRight, rowBottom) { draw() }
                            }
                        }
                        drawLine(line) { draw -> clipRect(wordRight, rowTop, Float.MAX_VALUE, rowBottom) { draw() } }
                        drawLine(line) { draw -> clipRect(-Float.MAX_VALUE, rowTop, wordLeft, rowBottom) { draw() } }
                    }
                    layout.shownLeavingRows.forEach { row ->
                        val position = Offset(0f, row.baseline - row.layout.firstBaseline)
                        fading.add(row.alpha) {
                            translate(left, top) {
                                row.boxes.forEach { box ->
                                    drawRoundRect(foldBoxColor, box.topLeft + Offset(0f, row.top), box.size, boxRadius)
                                }
                                drawText(textLayoutResult = row.layout, topLeft = position)
                            }
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
