package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
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
import kotlin.math.ceil
import kotlin.math.max

/**
 * A column of the bar next to the text, e.g. line numbers, warning markers or buttons to collapse a block.
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

private const val MIN_LINE_NUMBER_DIGITS = 3
private val LINE_NUMBER_PADDING = 4.dp

/**
 * The column of the line numbers, the default content of the bar. It always has room for three digits, so the text
 * does not jump around in short documents.
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

private class LineNumberColumn(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val padding: Float,
) : KodeEditorGutterColumn {
    private var measuredDigits = 0
    private var measuredWidth = 0

    override fun width(lineCount: Int): Int {
        val digits = max(lineCount.toString().length, MIN_LINE_NUMBER_DIGITS)
        if (digits != measuredDigits) {
            val text = measurer.measure("9".repeat(digits), style = style, softWrap = false)
            measuredWidth = ceil(text.size.width + 2 * padding).toInt()
            measuredDigits = digits
        }
        return measuredWidth
    }

    override fun DrawScope.drawCell(cell: GutterCell) {
        val number = measurer.measure("${cell.line + 1}", style = style, softWrap = false)
        drawText(
            textLayoutResult = number,
            topLeft = Offset(cell.width - padding - number.size.width, cell.baseline - number.firstBaseline),
        )
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
 * @param offsetY the current vertical scroll offset
 */
@Composable
internal fun EditorGutter(
    modifier: Modifier = Modifier,
    layout: KodeEditorLayout,
    columns: List<KodeEditorGutterColumn>,
    backgroundColor: Color,
    window: State<IntRect>,
    geometry: () -> EditorGeometry,
    offsetY: () -> Float,
) {
    // a change within a line does not move the bar, so typing does not record it again
    val lines = remember(layout) {
        derivedStateOf(structuralEqualityPolicy()) {
            val area = window.value
            gutterLinesWithin(layout, area.top.toFloat(), area.bottom.toFloat())
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
                    translationY = -offsetY() * current.zoom
                },
        ) {
            Box(
                modifier = Modifier
                    .placedAt { window.value.let { IntRect(0, it.top, geometry().gutterWidth, it.bottom) } }
                    .graphicsLayer {}
                    .drawBehind {
                        val stretch = cellStretch.value
                        val areaTop = window.value.top
                        lines.value.forEach { (line, top, baseline) ->
                            var start = 0f
                            columns.forEach { column ->
                                val width = column.width(layout.lineCount).toFloat()
                                val cell = GutterCell(
                                    line = line,
                                    width = width,
                                    height = layout.lineHeight / stretch,
                                    baseline = (baseline - top) / stretch,
                                )
                                withTransform({
                                    translate(start, top - areaTop)
                                    scale(1f, stretch, Offset.Zero)
                                }) {
                                    with(column) { drawCell(cell) }
                                }
                                start += width
                            }
                        }
                    },
            )
        }
    }
}
