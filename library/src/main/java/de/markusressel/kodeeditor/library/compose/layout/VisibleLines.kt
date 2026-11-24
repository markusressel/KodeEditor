package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
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
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.gutter.DecoratedLine
import de.markusressel.kodeeditor.library.compose.gutter.KodeEditorLineDecoration
import de.markusressel.kodeeditor.library.compose.search.KodeEditorSearch
import kotlinx.coroutines.flow.collectLatest

/** enough single line layouts for the lines of a whole window at the lowest zoom, so unchanged lines are reused */
internal const val LINE_LAYOUT_CACHE_SIZE = 2048

/** the corner radius of the box behind a placeholder, as a part of the line height */
private const val FOLD_BOX_RADIUS = 0.2f

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
                fun drawFadingRow(line: EditorLineLayout.DrawnLine, parts: List<EditorLineLayout.FadingPart>) {
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
                val cuts = layout.caretCuts()
                translate(left, top) {
                    layout.linesWithin(area).forEach { line ->
                        val parts = layout.movingParts(line)
                        if (parts == null) {
                            val fadingParts = layout.fadingParts(line, cuts)
                            if (fadingParts == null) drawRowContent(line) else drawFadingRow(line, fadingParts)
                        } else {
                            val rowAlpha = layout.rowAlpha(line.row)
                            // text that still appears keeps its alpha while it moves, or it flashes up for the move
                            val fadingParts = layout.fadingParts(line, cuts)
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
                                if (alpha >= 1f) drawPart() else fading.add(alpha) { translate(left, top) { drawPart() } }
                            }
                        }
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

/**
 * Draws the matches of a [KodeEditorSearch] in a layer of its own, below the selection and the text. A match in folded
 * lines marks the start of the row of its fold instead.
 *
 * @param layout where the text is
 * @param search the matches to draw
 * @param window the part of the document that is drawn
 * @param matchColor the color behind each match
 * @param currentColor the color behind the current match
 */
@Composable
internal fun SearchLayer(
    layout: EditorLineLayout,
    search: KodeEditorSearch,
    window: State<IntRect>,
    matchColor: Color,
    currentColor: Color,
) {
    Box(
        modifier = Modifier
            .placedAt { window.value }
            .graphicsLayer {}
            .drawBehind {
                val matches = search.matches
                if (matches.isEmpty()) return@drawBehind
                val current = search.current
                val area = window.value
                val first = layout.getLineForVerticalPosition(area.top.toFloat())
                val last = layout.getLineForVerticalPosition(area.bottom.toFloat())
                val start = layout.getLineStart(first)
                val end = layout.getLineEnd(last)
                val markWidth = FOLDED_MATCH_MARK_WIDTH.toPx()
                var lastMarkTop = Float.NaN
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    // the first match that ends behind the start of the drawn lines
                    var index = matches.binarySearchBy(start) { it.max }.let { if (it < 0) -it - 1 else it }
                    while (index < matches.size && matches[index].min <= end) {
                        val match = matches[index++]
                        val color = if (match == current) currentColor else matchColor
                        val line = layout.getLineForOffset(match.min)
                        if (layout.isLineHidden(line)) {
                            val top = layout.getLineTop(line) + layout.shownLineDistance(line)
                            if (top != lastMarkTop || match == current) {
                                drawRect(color, Offset(0f, top), Size(markWidth, layout.lineHeight))
                                lastMarkTop = top
                            }
                        } else {
                            drawPath(layout.shownPathForRange(match.min, match.max), color)
                        }
                    }
                }
            },
    )
}

/** how wide the mark of a match in folded lines is */
private val FOLDED_MATCH_MARK_WIDTH = 3.dp

/**
 * A part of a row drawn with an alpha of its own.
 *
 * @param left where it starts in the row
 * @param right where it ends in the row
 * @param alpha how visible it is, without [stops]
 * @param stops how visible it is along the row, from left to right: where in the row and the alpha there
 */
internal class FadingRun(val left: Float, val right: Float, val alpha: Float, val stops: List<Pair<Float, Float>>?)

/**
 * Joins the parts of [parts] that are one behind the other in the row. Each has its alpha in its middle, and the alpha
 * goes from one to the next in a straight line, so neighbours that appear a bit apart have no edge between them. Parts
 * that are not drawn (alpha 0) are not joined.
 */
internal fun gradientRuns(parts: List<EditorLineLayout.FadingPart>): List<FadingRun> {
    val sorted = parts.filter { it.alpha > 0f && it.right > it.left }.sortedBy { it.left }
    val runs = ArrayList<FadingRun>()
    var index = 0
    while (index < sorted.size) {
        var last = index
        while (last + 1 < sorted.size && sorted[last + 1].left <= sorted[last].right + GRADIENT_GAP) last++
        val first = sorted[index]
        if (last == index) {
            runs += FadingRun(first.left, first.right, first.alpha, null)
        } else {
            val stops = ArrayList<Pair<Float, Float>>(last - index + 3)
            stops += first.left to first.alpha
            for (part in index..last) {
                val piece = sorted[part]
                stops += (piece.left + piece.right) / 2 to piece.alpha
            }
            stops += sorted[last].right to sorted[last].alpha
            runs += FadingRun(first.left, sorted[last].right, first.alpha, stops)
        }
        index = last + 1
    }
    return runs
}

/** how far apart two parts can be and still be one behind the other, in px */
private const val GRADIENT_GAP = 0.5f
