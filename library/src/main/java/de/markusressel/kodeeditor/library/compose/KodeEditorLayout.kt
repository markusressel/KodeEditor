package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * Where the text of a [KodeEditor] is, in the coordinates of the overlay.
 *
 * All rows have the same height and only the rows that are asked for are measured, so this works at any length of
 * the text. A style with a larger font size does not make its row taller. A line is one row, or several rows if the
 * editor wraps its lines.
 *
 * Reading from this in a composable or in a draw phase updates it when the text changes.
 */
@Stable
interface KodeEditorLayout {
    /** the text this describes */
    val text: String

    /** the number of lines */
    val lineCount: Int

    /** the height of each row, which is a whole line unless the editor wraps it */
    val lineHeight: Float

    /** the size of the whole document */
    val contentSize: IntSize

    /**
     * @param offset a position in the text
     * @return the line of that position
     */
    fun getLineForOffset(offset: Int): Int

    /**
     * @param y a vertical position
     * @return the line at that position, the nearest one outside of the text
     */
    fun getLineForVerticalPosition(y: Float): Int

    /**
     * @param line a line
     * @return the position in the text where the line starts
     */
    fun getLineStart(line: Int): Int

    /**
     * @param line a line
     * @return the position in the text where the line ends, in front of its line break
     */
    fun getLineEnd(line: Int): Int

    /**
     * @param line a line
     * @return the top of the first row of the line
     */
    fun getLineTop(line: Int): Float

    /**
     * @param line a line
     * @return the bottom of the last row of the line
     */
    fun getLineBottom(line: Int): Float

    /**
     * @param line a line
     * @return the baseline of the first row of the line
     */
    fun getLineBaseline(line: Int): Float

    /**
     * @param offset a position in the text
     * @return where a cursor at that position is, with a width of zero
     */
    fun getCursorRect(offset: Int): Rect

    /**
     * Only the part of the range near the visible area is included, measuring all lines of a large range would take
     * too long. Read this in a composable or a draw phase to get the part that is needed at the moment.
     *
     * @param start the start of the range
     * @param end the end of the range
     * @return the shape that covers the range
     */
    fun getPathForRange(start: Int, end: Int): Path

    /**
     * @param position a point
     * @return the position in the text closest to the point
     */
    fun getOffsetForPosition(position: Offset): Int

    /**
     * @param offset a position in the text
     * @return the word at that position, a single character if there is no word
     */
    fun getWordBoundary(offset: Int): TextRange
}

/**
 * @param measurer measures the lines, and caches them by their content, which makes unchanged lines cheap
 * @param textStyle the style of the text
 * @param highlighting the styles of the text
 * @param window the part of the document that is drawn
 * @param breaker finds where lines break, null if they don't wrap
 * @param wrapWidth the width to wrap the lines at
 */
internal class EditorLineLayout(
    private val state: KodeEditorState,
    private val measurer: TextMeasurer,
    private val textStyle: TextStyle,
    private val highlighting: EditorHighlighting,
    private val window: State<IntRect>,
    breaker: RowBreaker? = null,
    private val wrapWidth: () -> Float = { 0f },
) : KodeEditorLayout {

    private val reference = measurer.measure("X", style = textStyle, softWrap = false)
    override val lineHeight = reference.size.height.toFloat().coerceAtLeast(1f)
    private val baseline = reference.firstBaseline

    private val lineCountState = derivedStateOf { state.lineCount }

    private val unwrappedRows = UnwrappedRows(state)
    private val wrappedRows = breaker?.let { WrappedRows(state, it::breaks) }

    private val rows: EditorRows
        get() {
            // the rows are no state of their own, whoever reads them depends on what they are made from
            state.version
            val wrapped = wrappedRows ?: return unwrappedRows
            wrapped.update(wrapWidth())
            return wrapped
        }

    /** tells the rows about changes of the text */
    val rowObserver: ((TextSplice) -> Unit)? = wrappedRows?.let { it::onSplice }

    /** lines with few wide characters can be wider than the one with the most characters */
    private var widestDrawnLine by mutableFloatStateOf(0f)

    override val text: String get() = state.text

    override val lineCount: Int get() = lineCountState.value

    override val contentSize: IntSize
        get() {
            val height = (rows.count * lineHeight).toInt()
            if (wrappedRows != null) {
                // only a style with a larger font can make a row wider, a pixel is the rounding of its width
                val width = wrapWidth()
                val widest = widestDrawnLine
                return IntSize(ceil(if (widest > width + 1f) widest else width).toInt(), height)
            }
            val longest = measureLine(state.longestLine()).size.width.toFloat()
            val width = maxOf(longest, widestDrawnLine)
            return IntSize(ceil(width).toInt(), height)
        }

    /**
     * @param y a vertical position before the width of the wrapped lines changed
     * @return the position of the same text now, null if the width did not change since the last call
     */
    fun takePositionAfterResize(y: Float): Float? {
        val wrapped = wrappedRows ?: return null
        wrapped.update(wrapWidth())
        val position = wrapped.takePositionAfterResize(y / lineHeight) ?: return null
        widestDrawnLine = 0f
        return position * lineHeight
    }

    override fun getLineForOffset(offset: Int) = state.lineOf(offset)

    override fun getLineForVerticalPosition(y: Float) = rows.lineOf(rowAt(y))

    override fun getLineStart(line: Int) = state.lineStart(line)

    override fun getLineEnd(line: Int) = state.lineEnd(line)

    override fun getLineTop(line: Int) = rows.firstRow(line) * lineHeight

    override fun getLineBottom(line: Int) = (rows.lastRow(line) + 1) * lineHeight

    override fun getLineBaseline(line: Int) = rows.firstRow(line) * lineHeight + baseline

    override fun getCursorRect(offset: Int): Rect {
        val row = rows.rowOf(offset)
        val x = xOf(row, offset)
        return Rect(x, row * lineHeight, x, (row + 1) * lineHeight)
    }

    /** the number of rows, which are what moving the cursor up and down goes through */
    val rowCount: Int get() = rows.count

    fun rowOf(offset: Int) = rows.rowOf(offset)

    fun rowAt(y: Float) = (y / lineHeight).toInt().coerceIn(0, rows.count - 1)

    fun rowTop(row: Int) = row * lineHeight

    /** where the row of [offset] starts */
    fun rowStart(offset: Int) = rows.start(rows.rowOf(offset))

    /** the last position that is shown in the row of [offset] */
    fun rowEnd(offset: Int) = lastPositionIn(rows.rowOf(offset))

    /** The position at the end of a row that continues is the start of the next row, so it is not a part of this one */
    private fun lastPositionIn(row: Int): Int {
        val rows = rows
        val end = rows.end(row)
        if (row == rows.lastRow(rows.lineOf(row))) return end
        val before = end - 1
        return if (before > rows.start(row) && state.text[before].isLowSurrogate()) before - 1 else before
    }

    private fun xOf(row: Int, offset: Int): Float {
        val start = rows.start(row)
        val inRow = offset.coerceIn(start, rows.end(row)) - start
        return measureRow(row).getHorizontalPosition(inRow, usePrimaryDirection = true)
    }

    override fun getPathForRange(start: Int, end: Int): Path {
        val path = Path()
        val first = minOf(start, end).coerceIn(0, state.text.length)
        val last = maxOf(start, end).coerceIn(0, state.text.length)
        if (first == last) return path
        val area = window.value
        val rows = rows
        val shown = maxOf(rows.rowOf(first), rowAt(area.top.toFloat()))..
            minOf(rows.rowOf(last), rowAt(area.bottom.toFloat()))
        for (row in shown) {
            val rowStart = rows.start(row)
            val rowEnd = rows.end(row)
            val left = if (first > rowStart) xOf(row, first) else xOf(row, rowStart)
            // a selected line break is shown as a bit of space, so an empty line can be seen as selected
            val lineBreak = if (row == rows.lastRow(rows.lineOf(row))) lineHeight / 4 else 0f
            val right = if (last > rowEnd) xOf(row, rowEnd) + lineBreak else xOf(row, last)
            path.addRect(Rect(minOf(left, right), row * lineHeight, maxOf(left, right), (row + 1) * lineHeight))
        }
        return path
    }

    override fun getOffsetForPosition(position: Offset): Int {
        val row = rowAt(position.y)
        val inRow = measureRow(row).getOffsetForPosition(Offset(position.x, lineHeight / 2))
        return (rows.start(row) + inRow).coerceAtMost(lastPositionIn(row))
    }

    override fun getWordBoundary(offset: Int): TextRange = wordAt(state.text, offset)

    /** the styled single line layout of [line], which is not wrapped */
    private fun measureLine(line: Int): TextLayoutResult = measureRow(line)

    /** the styled single line layout of [row] */
    private fun measureRow(row: Int): TextLayoutResult {
        val text = lineTexts(state, row..row, currentStyles(), state.composition, rows).single()
        return measurer.measure(text, style = textStyle, softWrap = false)
    }

    private fun currentStyles() = highlighting.styles

    /**
     * @param area the part of the document to draw
     * @return the rows within the area, each with its layout and where to draw it
     */
    fun linesWithin(area: IntRect): List<DrawnLine> {
        val shown = rowAt(area.top.toFloat())..rowAt(area.bottom.toFloat())
        val texts = lineTexts(state, shown, currentStyles(), state.composition, rows)
        return texts.mapIndexedNotNull { index, line ->
            if (line.isEmpty()) return@mapIndexedNotNull null
            val layout = measurer.measure(line, style = textStyle, softWrap = false)
            val width = layout.size.width.toFloat()
            if (width > widestDrawnLine) widestDrawnLine = width
            DrawnLine(layout, Offset(0f, (shown.first + index) * lineHeight + baseline - layout.firstBaseline))
        }
    }

    class DrawnLine(val layout: TextLayoutResult, val position: Offset)
}

/**
 * The text and the styles of each of the given rows.
 *
 * Styles are not sorted by line, so this looks at each one once, for all rows together.
 *
 * @param state the text
 * @param lines the rows to get
 * @param styles the styles of the whole text
 * @param composition the text the keyboard is composing, which gets underlined
 * @param rows the rows of the text, by default each line is one
 */
internal fun lineTexts(
    state: KodeEditorState,
    lines: IntRange,
    styles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    composition: TextRange? = null,
    rows: EditorRows = UnwrappedRows(state),
): List<AnnotatedString> {
    val count = lines.count()
    val first = rows.start(lines.first)
    val last = rows.end(lines.last)
    val spans = arrayOfNulls<MutableList<AnnotatedString.Range<SpanStyle>>>(count)

    fun add(style: SpanStyle, start: Int, end: Int) {
        if (end <= first || start >= last) return
        val startLine = rows.rowOf(start.coerceAtLeast(first)) - lines.first
        val endLine = rows.rowOf((end - 1).coerceAtMost(last - 1)) - lines.first
        for (index in startLine.coerceAtLeast(0)..endLine.coerceAtMost(count - 1)) {
            val line = lines.first + index
            val lineStart = rows.start(line)
            val from = start.coerceAtLeast(lineStart) - lineStart
            val to = end.coerceAtMost(rows.end(line)) - lineStart
            if (from < to) {
                val list = spans[index] ?: mutableListOf<AnnotatedString.Range<SpanStyle>>().also { spans[index] = it }
                list += AnnotatedString.Range(style, from, to)
            }
        }
    }

    styles.forEach { add(it.item, it.start, it.end) }
    composition?.let { add(COMPOSING_STYLE, it.min, it.max) }

    val text = state.text
    return List(count) { index ->
        val line = lines.first + index
        AnnotatedString(
            text = text.substring(rows.start(line), rows.end(line)),
            spanStyles = spans[index].orEmpty(),
        )
    }
}

private val COMPOSING_STYLE = SpanStyle(textDecoration = TextDecoration.Underline)

/** the word at [offset], or the single character there if it is not a part of a word */
internal fun wordAt(text: String, offset: Int): TextRange {
    if (text.isEmpty()) return TextRange(0)
    val position = offset.coerceIn(0, text.length)
    val inWord = (position < text.length && text[position].isWordPart()) ||
        (position > 0 && text[position - 1].isWordPart())
    if (inWord) {
        var start = position
        var end = position
        while (start > 0 && text[start - 1].isWordPart()) start--
        while (end < text.length && text[end].isWordPart()) end++
        return TextRange(start, end)
    }
    return when {
        position < text.length && text[position] != '\n' -> TextRange(position, position + 1)
        position > 0 && text[position - 1] != '\n' -> TextRange(position - 1, position)
        else -> TextRange(position)
    }
}

/** moving the cursor with the keys uses the same rows the editor shows */
internal class EditorTextLines(private val layout: EditorLineLayout) : TextLines {
    override val lineCount get() = layout.rowCount
    override fun lineOf(offset: Int) = layout.rowOf(offset)
    override fun lineAt(y: Float) = layout.rowAt(y)
    override fun topOf(line: Int) = layout.rowTop(line)
    override fun xOf(offset: Int) = layout.getCursorRect(offset).left
    override fun offsetAt(line: Int, x: Float) =
        layout.getOffsetForPosition(Offset(x, layout.rowTop(line) + layout.lineHeight / 2))
    override fun startOf(offset: Int) = layout.rowStart(offset)
    override fun endOf(offset: Int) = layout.rowEnd(offset)
}
