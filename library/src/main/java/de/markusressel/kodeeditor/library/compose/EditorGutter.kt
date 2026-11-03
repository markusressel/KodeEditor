package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A column of the bar next to the text, e.g. line numbers, warning markers or bookmarks.
 *
 * Columns draw instead of composing, so the bar stays cheap with a lot of visible lines. Reading snapshot state in
 * [drawCell] draws the bar again when that state changes, so markers only have to be kept in a state.
 */
@Stable
interface KodeEditorGutterColumn {
    /**
     * @param lineCount the number of lines of the document
     * @return the width of this column in pixels at zoom 1
     */
    fun width(lineCount: Int): Int

    /**
     * Draws what this column shows for one line, with the top left corner of the cell at the origin.
     * The cell is on the first row of the line if the editor wraps it.
     *
     * @param cell the line and the size of the cell
     */
    fun DrawScope.drawCell(cell: GutterCell)

    /**
     * @param line the line that was tapped
     * @return true if this column did something with the tap, otherwise it places the cursor like any tap on the bar
     */
    fun onTap(line: Int): Boolean = false
}

/**
 * The space a column can draw into for one line
 *
 * @param line the line
 * @param width the width of the cell
 * @param height the height of the cell
 * @param baseline where the baseline of the text of the line is, for drawing text next to it
 */
class GutterCell(
    val line: Int,
    val width: Float,
    val height: Float,
    val baseline: Float,
)

private val LINE_NUMBER_PADDING = 4.dp

/**
 * The column of the line numbers, the default content of the bar. It is as wide as the highest line number needs.
 *
 * @param textStyle the style of the text of the editor
 * @param colors the colors of the editor
 * @param enabled whether the editor is enabled
 */
@Composable
fun rememberLineNumberColumn(
    textStyle: TextStyle = LocalTextStyle.current,
    colors: KodeEditorColors = KodeEditorDefaults.editorColors(),
    enabled: Boolean = true,
): KodeEditorGutterColumn {
    val fieldColor = colors.textFieldColors(enabled = enabled).value.textColor(enabled).value
    val color = colors.lineNumberTextColor().value.takeOrElse { textStyle.color.takeOrElse { fieldColor } }
    val style = textStyle.copy(color = color)
    val padding = LocalDensity.current.run { LINE_NUMBER_PADDING.toPx() }
    val measurer = rememberTextMeasurer(cacheSize = LINE_LAYOUT_CACHE_SIZE)
    return remember(measurer, style, padding) { LineNumberColumn(measurer, style, padding) }
}

internal class LineNumberColumn(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val padding: Float,
) : KodeEditorGutterColumn {
    private var measuredDigits = 0
    private var measuredWidth = 0

    override fun width(lineCount: Int): Int {
        val digits = lineCount.toString().length
        if (digits != measuredDigits) {
            val text = measurer.measure("9".repeat(digits), style = style, softWrap = false)
            measuredWidth = ceil(text.size.width + 2 * padding).toInt()
            measuredDigits = digits
        }
        return measuredWidth
    }

    override fun DrawScope.drawCell(cell: GutterCell) = drawNumber(cell, alpha = 1f)

    /** Draws the number of the line of the [cell], which can be a line that was just removed */
    fun DrawScope.drawNumber(cell: GutterCell, alpha: Float) {
        val number = measurer.measure("${cell.line + 1}", style = style, softWrap = false)
        drawText(
            textLayoutResult = number,
            topLeft = Offset(cell.width - padding - number.size.width, cell.baseline - number.firstBaseline),
            alpha = alpha,
        )
    }
}

/**
 * How visible the numbers of new and removed lines are while they fade. A change of more than [MAX_FADING_LINES] lines
 * does not fade, it would show nothing more on the screen and only cost time.
 *
 * @param lineCount the number of lines of the document
 */
internal class LineNumberFades(private var lineCount: Int) {
    private class Fade(val from: Float, val to: Float) {
        var startMs = -1L
        var alpha = from
    }

    private val fades = HashMap<Int, Fade>()

    /** whether a line is still fading */
    val active: Boolean get() = fades.isNotEmpty()

    /** the lines behind the end of the document that are still visible, from top to bottom */
    val removedLines: List<Int> get() = fades.keys.filter { it >= lineCount }.sorted()

    /** @param count the new number of lines of the document */
    fun onLineCountChanged(count: Int) {
        val previous = lineCount
        lineCount = count
        if (abs(count - previous) > MAX_FADING_LINES) {
            fades.clear()
            return
        }
        val appearing = count > previous
        for (line in min(count, previous) until max(count, previous)) {
            // a line that changes direction continues from where it is
            val current = fades[line]?.alpha ?: if (appearing) 0f else 1f
            fades[line] = Fade(from = current, to = if (appearing) 1f else 0f)
        }
    }

    /** @param nowMs the time of the frame */
    fun onFrame(nowMs: Long) {
        fades.values.removeAll { fade ->
            if (fade.startMs < 0) fade.startMs = nowMs
            val progress = ((nowMs - fade.startMs) / GUTTER_ANIMATION_MS.toFloat()).coerceAtMost(1f)
            fade.alpha = fade.from + (fade.to - fade.from) * progress
            progress >= 1f
        }
    }

    /** @return how visible the number of the [line] is */
    fun alpha(line: Int): Float = fades[line]?.alpha ?: 1f

    private companion object {
        const val MAX_FADING_LINES = 256
    }
}

/**
 * @param columns the columns of the bar
 * @param lineCount the number of lines of the document
 * @param x a position in the bar in pixels at zoom 1
 * @return the column at that position, null behind the last one
 */
internal fun gutterColumnAt(columns: List<KodeEditorGutterColumn>, lineCount: Int, x: Float): KodeEditorGutterColumn? {
    var end = 0f
    return columns.firstOrNull { column ->
        end += column.width(lineCount)
        x < end
    }
}

/** A line that the bar shows, with the top and the baseline of its first row */
internal data class GutterLine(val line: Int, val top: Float, val baseline: Float)

/** @return the lines whose first row is within the [top] and [bottom] of the document */
internal fun gutterLinesWithin(layout: KodeEditorLayout, top: Float, bottom: Float): List<GutterLine> {
    val first = layout.getLineForVerticalPosition(top)
    val last = layout.getLineForVerticalPosition(bottom)
    return (first..last).mapNotNull { line ->
        if (layout.isLineHidden(line)) return@mapNotNull null
        val lineTop = layout.getLineTop(line)
        // a wrapped line that starts above the window has nothing to show in it
        if (lineTop + layout.lineHeight < top) null else GutterLine(line, lineTop, layout.getLineBaseline(line))
    }
}

/**
 * The bar next to the text. It covers the whole height of the editor, and follows the text vertically. It has no
 * touch handling of its own, so the gestures of the editor work on it too.
 *
 * @param modifier compose modifiers, it should fill the editor
 * @param layout where the lines are
 * @param columns what the bar shows
 * @param backgroundColor the color of the bar
 * @param window the part of the document to record for drawing, in text coordinates
 * @param geometry where things are in the editor
 * @param targetWidth the width of the bar at zoom 1 when its animation ends, which the columns are laid out for
 * @param offsetY the current vertical scroll offset
 * @param fadeLineNumbers whether the numbers of new and removed lines fade
 */
@Composable
internal fun EditorGutter(
    modifier: Modifier = Modifier,
    layout: EditorLineLayout,
    columns: List<KodeEditorGutterColumn>,
    backgroundColor: Color,
    window: State<IntRect>,
    geometry: () -> EditorGeometry,
    targetWidth: () -> Int,
    offsetY: () -> Float,
    fadeLineNumbers: Boolean = true,
) {
    // a change within a line does not move the bar, so typing does not record it again
    val lines = remember(layout) {
        derivedStateOf(structuralEqualityPolicy()) {
            val area = window.value
            gutterLinesWithin(layout, area.top.toFloat(), area.bottom.toFloat())
        }
    }
    val lineCount = layout.lineCount
    val fades = remember(layout, fadeLineNumbers) { LineNumberFades(lineCount) }
    var fadeFrame by remember { mutableLongStateOf(0L) }
    SideEffect { if (fadeLineNumbers) fades.onLineCountChanged(lineCount) }
    LaunchedEffect(fades, lineCount) {
        while (fades.active) {
            withFrameMillis { now ->
                fades.onFrame(now)
                fadeFrame = now
            }
        }
    }

    // changes only while the bar stopped growing with the zoom
    val cellStretch = remember { derivedStateOf { geometry().let { it.gutterScale / it.zoom } } }

    Box(
        modifier = modifier.drawBehind {
            drawRect(backgroundColor, size = Size(geometry().gutterOnScreen, size.height))
        },
    ) {
        if (columns.isEmpty()) return@Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val current = geometry()
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = current.gutterScale
                    scaleY = current.zoom
                    // the columns stay next to the text while the bar animates, the left edge moves out of the editor
                    translationX = (current.gutterWidth - targetWidth()) * current.gutterScale
                    translationY = -offsetY() * current.zoom
                },
        ) {
            Box(
                modifier = Modifier
                    .placedAt { window.value.let { IntRect(0, it.top, targetWidth(), it.bottom) } }
                    .graphicsLayer {}
                    .drawBehind {
                        // a read, so that the bar is drawn again on each frame of a fade
                        fadeFrame
                        val stretch = cellStretch.value
                        val areaTop = window.value.top

                        val fading = FadingDraws()

                        fun drawLine(
                            line: Int,
                            top: Float,
                            baseline: Float,
                            removed: Boolean,
                            alpha: Float = fades.alpha(line),
                        ) {
                            var start = 0f
                            columns.forEach { column ->
                                val width = column.width(layout.lineCount).toFloat()
                                // other columns only know the lines of the document
                                if (column is LineNumberColumn || !removed) {
                                    val cell = GutterCell(
                                        line = line,
                                        width = width,
                                        height = layout.lineHeight / stretch,
                                        baseline = (baseline - top) / stretch,
                                    )
                                    val cellStart = start
                                    val draw = {
                                        withTransform({
                                            translate(cellStart, top - areaTop)
                                            scale(1f, stretch, Offset.Zero)
                                        }) {
                                            if (column is LineNumberColumn) {
                                                with(column) { drawNumber(cell, alpha) }
                                            } else {
                                                with(column) { drawCell(cell) }
                                            }
                                        }
                                    }
                                    if (column is LineNumberColumn || alpha >= 1f) {
                                        draw()
                                    } else {
                                        fading.add(alpha, draw)
                                    }
                                }
                                start += width
                            }
                        }

                        lines.value.forEach { (line, top, baseline) ->
                            // the bar moves with the text when lines wrap differently
                            val distance = layout.shownLineDistance(line)
                            val alpha = fades.alpha(line) * layout.lineAlpha(line)
                            drawLine(line, top + distance, baseline + distance, removed = false, alpha = alpha)
                        }

                        // the bar shows the lines that a fold just hid until they are gone
                        layout.shownLeavingRows.forEach { row ->
                            if (row.firstOfLine) {
                                drawLine(row.line, row.top, row.baseline, removed = false, alpha = row.alpha)
                            }
                        }

                        // removed lines have no place in the document anymore, they fade below its end
                        val lastLine = layout.lineCount - 1
                        val end = layout.getLineBottom(lastLine)
                        val baselineInRow = layout.getLineBaseline(lastLine) - layout.getLineTop(lastLine)
                        fades.removedLines.forEach { line ->
                            val top = end + (line - lastLine - 1) * layout.lineHeight
                            drawLine(line, top, top + baselineInRow, removed = true)
                        }

                        with(fading) { drawAll() }
                    },
            )
        }
    }
}
