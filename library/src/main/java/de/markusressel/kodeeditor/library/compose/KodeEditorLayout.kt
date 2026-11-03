package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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

    /**
     * The places of a hidden line and its positions are at the end of the line that hides it.
     *
     * @param line a line
     * @return whether the line is hidden by a fold
     */
    fun isLineHidden(line: Int): Boolean = false
}

/**
 * @param measurer measures the lines, and caches them by their content, which makes unchanged lines cheap
 * @param textStyle the style of the text
 * @param highlighting the styles of the text
 * @param window the part of the document that is drawn
 * @param breaker finds where lines break, null if they don't wrap
 * @param wrapWidth the width to wrap the lines at
 * @param animateReflow whether what is shown moves to its new place when lines wrap differently
 * @param folds what is folded, null if the editor does not fold
 */
internal class EditorLineLayout(
    private val state: KodeEditorState,
    private val measurer: TextMeasurer,
    private val textStyle: TextStyle,
    private val highlighting: EditorHighlighting,
    private val window: State<IntRect>,
    breaker: RowBreaker? = null,
    private val wrapWidth: () -> Float = { 0f },
    private val animateReflow: Boolean = false,
    private val folds: EditorFolds? = null,
) : KodeEditorLayout {

    private val reference = measurer.measure("X", style = textStyle, softWrap = false)
    override val lineHeight = reference.size.height.toFloat().coerceAtLeast(1f)
    private val baseline = reference.firstBaseline

    private val lineCountState = derivedStateOf { state.lineCount }

    private val unwrappedRows = UnwrappedRows(state)
    private val wrappedRows = breaker?.let {
        WrappedRows(
            state = state,
            onReflow = { firstRows, breaks ->
                if (animateReflow) {
                    val shownFirstRows = folds?.hidden?.shownFirstRows(firstRows) ?: firstRows
                    reflow = ReflowTransition(state, shownFirstRows, breaks, reflow, lineHeight, ::measureBetween)
                }
            },
            sameBreaks = it::breakAlike,
            hiddenLines = { folds?.hidden },
            breaker = it::breaks,
        )
    }

    private var reflow: ReflowTransition? = null

    /** whoever shows something at its moving place reads this, and is updated on each frame of a reflow */
    private var reflowFrame by mutableLongStateOf(0L)

    private val activeReflow: ReflowTransition?
        get() {
            reflowFrame
            rows
            return reflow?.takeIf { it.valid }
        }

    /** the text between two positions of a row, without its styles, which is enough to know where it was */
    private fun measureBetween(start: Int, end: Int): Float {
        if (start == end) return 0f
        val text = state.text.substring(start, end)
        return measurer.measure(text, style = textStyle, softWrap = false)
            .getHorizontalPosition(text.length, usePrimaryDirection = true)
    }

    /** the width the lines currently wrap at */
    val currentWrapWidth: Float get() = wrapWidth()

    /** whether the text is moving to where a change of the wrap width put it */
    val reflowing: Boolean
        get() {
            rows
            return reflow != null
        }

    /** @param nowMs the time of the frame */
    fun onReflowFrame(nowMs: Long) {
        val current = reflow ?: return
        current.onFrame(nowMs)
        if (!current.valid || current.progress >= 1f) reflow = null
        reflowFrame = nowMs
    }

    /** @param distance how far the view scrolled to keep its text in place after the lines wrapped differently */
    fun onScrolledByReflow(distance: Float) {
        reflow?.let { it.scrolled += distance }
    }

    private val rows: EditorRows
        get() {
            // the rows are no state of their own, whoever reads them depends on what they are made from
            state.version
            val wrapped = wrappedRows
            wrapped?.update(wrapWidth())
            val base = wrapped ?: unwrappedRows
            val hidden = folds?.hidden
            if (hidden !== rowsHidden) {
                // where things come from is about other rows now
                reflow = null
                rowsHidden = hidden
                foldedRows = null
            }
            hidden ?: return base
            val generation = wrapped?.generation ?: 0
            if (foldedRows == null || foldedGeneration != generation) {
                foldedRows = FoldedRows(base, hidden)
                foldedGeneration = generation
            }
            return foldedRows!!
        }

    private var rowsHidden: HiddenLines? = null
    private var foldedRows: FoldedRows? = null
    private var foldedGeneration = -1

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
     * @param anchors positions in the text that should stay where they are, the first visible one is used
     * @param visibleHeight how much of the document is visible below [y]
     * @return the position that keeps the first visible anchor in its place, otherwise the position of the same text at
     * [y], null if the width did not change since the last call
     */
    fun takePositionAfterResize(y: Float, anchors: List<Int>, visibleHeight: Float): Float? {
        val wrapped = wrappedRows ?: return null
        wrapped.update(wrapWidth())
        val position = wrapped.takePositionAfterResize(y / lineHeight, anchors, visibleHeight / lineHeight)
            ?: return null
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

    override fun getPathForRange(start: Int, end: Int) = pathForRange(start, end, moving = false)

    /** like [getPathForRange], at the place where the range is on the screen during a reflow */
    fun shownPathForRange(start: Int, end: Int) = pathForRange(start, end, moving = true)

    /** like [getCursorRect], at the place where the cursor is on the screen during a reflow */
    fun shownCursorRect(offset: Int): Rect {
        val rect = getCursorRect(offset)
        val transition = activeReflow ?: return rect
        return rect.translate(distanceOf(transition, offset, rect.left, rect.top))
    }

    /** @return how far the start of the [line] is away from its place on the screen during a reflow */
    fun shownLineDistance(line: Int): Float {
        val transition = activeReflow ?: return 0f
        val top = getLineTop(line)
        return distanceOf(transition, getLineStart(line), 0f, top).y
    }

    private fun distanceOf(transition: ReflowTransition, offset: Int, x: Float, y: Float): Offset {
        val origin = transition.startOf(offset)
        val remaining = 1f - transition.progress
        return Offset((origin.x - x) * remaining, (origin.y - y) * remaining)
    }

    private fun pathForRange(start: Int, end: Int, moving: Boolean): Path {
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
            val transition = if (moving) activeReflow else null
            if (transition == null) {
                path.addRect(Rect(minOf(left, right), row * lineHeight, maxOf(left, right), (row + 1) * lineHeight))
            } else {
                addMovingRects(path, transition, row, first, last, right)
            }
        }
        return path
    }

    /** adds the selected parts of the [row], each at the place it moves through */
    private fun addMovingRects(path: Path, transition: ReflowTransition, row: Int, first: Int, last: Int, right: Float) {
        val rows = rows
        val from = maxOf(first, rows.start(row))
        val to = minOf(last, rows.end(row))
        val y = row * lineHeight
        val parts = insideBreaks(transition, row).filter { it in (from + 1) until to }
        (listOf(from) + parts).forEachIndexed { index, partStart ->
            val partLeft = xOf(row, partStart)
            val partRight = parts.getOrNull(index)?.let { xOf(row, it) } ?: right
            val distance = distanceOf(transition, partStart, partLeft, y)
            path.addRect(
                Rect(minOf(partLeft, partRight), y, maxOf(partLeft, partRight), y + lineHeight).translate(distance),
            )
        }
    }

    /** @return the positions in the [row] where a line broke before the reflow */
    private fun insideBreaks(transition: ReflowTransition, row: Int): List<Int> {
        val rows = rows
        val start = rows.start(row)
        val end = rows.end(row)
        val line = rows.lineOf(row)
        val breaks = transition.breaksOf(line) ?: return emptyList()
        val lineStart = state.lineStart(line)
        return breaks.map { lineStart + it }.filter { it in (start + 1) until end }
    }

    override fun getOffsetForPosition(position: Offset): Int {
        val row = rowAt(position.y)
        val inRow = measureRow(row).getOffsetForPosition(Offset(position.x, lineHeight / 2))
        return (rows.start(row) + inRow).coerceAtMost(lastPositionIn(row))
    }

    override fun getWordBoundary(offset: Int): TextRange = wordAt(state.text, offset)

    override fun isLineHidden(line: Int): Boolean = folds?.isHidden(line) ?: false

    /** the next position from [offset] in the given direction that is not folded away */
    fun shownPosition(offset: Int, forward: Boolean) = folds?.shownPosition(offset, forward) ?: offset

    /**
     * @param area the part of the document to draw, [linesWithin] has to be called for it first
     * @param decorations what shows text behind the lines
     * @return the text behind the lines within the area
     */
    fun trailingTextsWithin(area: IntRect, decorations: List<KodeEditorLineDecoration>): List<TrailingText> {
        if (decorations.isEmpty()) return emptyList()
        val rows = rows
        val transition = activeReflow
        val result = ArrayList<TrailingText>()
        var drawn = 0
        for (row in rowAt(area.top.toFloat())..rowAt(area.bottom.toFloat())) {
            val line = rows.lineOf(row)
            if (row != rows.lastRow(line)) continue
            while (drawn < drawnLines.size && drawnLines[drawn].row < row) drawn++
            // an empty row has no layout
            val end = drawnLines.getOrNull(drawn)?.takeIf { it.row == row }?.layout?.size?.width?.toFloat() ?: 0f
            var x = end
            val y = row * lineHeight
            val distance = transition?.let { distanceOf(it, rows.end(row), end, y) } ?: Offset.Zero
            decorations.forEach { decoration ->
                val text = decoration.trailingText(line) ?: return@forEach
                val layout = measurer.measure(text, style = textStyle, softWrap = false)
                result += TrailingText(layout, Offset(x, y + baseline - layout.firstBaseline) + distance)
                x += layout.size.width
            }
            if (wrappedRows == null && x > widestDrawnLine) widestDrawnLine = x
        }
        return result
    }

    /**
     * @param position a point
     * @param decorations what shows text behind the lines
     * @return true if there is text of a decoration at the point, and the decoration did something with the tap
     */
    fun tapTrailingText(position: Offset, decorations: List<KodeEditorLineDecoration>): Boolean {
        if (decorations.isEmpty()) return false
        val rows = rows
        val row = rowAt(position.y)
        val line = rows.lineOf(row)
        if (row != rows.lastRow(line)) return false
        val texts = decorations.mapNotNull { decoration -> decoration.trailingText(line)?.let { decoration to it } }
        var x = xOf(row, rows.end(row))
        texts.forEachIndexed { index, (decoration, text) ->
            val width = measurer.measure(text, style = textStyle, softWrap = false).size.width
            // something small next to a line is hard to hit with a finger
            val slack = if (index == texts.lastIndex) lineHeight else 0f
            if (position.x >= x && position.x <= x + width + slack) return decoration.onTrailingTextTap(line)
            x += width
        }
        return false
    }

    /** the styled single line layout of [line], which is not wrapped */
    private fun measureLine(line: Int): TextLayoutResult {
        val text = lineTexts(state, line..line, currentStyles(), state.composition, unwrappedRows).single()
        return measurer.measure(text, style = textStyle, softWrap = false)
    }

    /** the styled single line layout of [row] */
    private fun measureRow(row: Int): TextLayoutResult {
        val text = lineTexts(state, row..row, currentStyles(), state.composition, rows).single()
        return measurer.measure(text, style = textStyle, softWrap = false)
    }

    private fun currentStyles() = highlighting.styles

    /** what [linesWithin] found last, a reflow draws the same rows on all of its frames */
    private var drawnArea: IntRange? = null
    private var drawnVersion = -1
    private var drawnGeneration = -1
    private var drawnStyles: List<AnnotatedString.Range<SpanStyle>>? = null
    private var drawnComposition: TextRange? = null
    private var drawnHidden: HiddenLines? = null
    private var drawnLines = emptyList<DrawnLine>()

    /**
     * @param area the part of the document to draw
     * @return the rows within the area, each with its layout and where to draw it
     */
    fun linesWithin(area: IntRect): List<DrawnLine> {
        val rows = rows
        val shown = rowAt(area.top.toFloat())..rowAt(area.bottom.toFloat())
        val styles = currentStyles()
        val composition = state.composition
        val generation = wrappedRows?.generation ?: 0
        if (
            shown == drawnArea && state.rawVersion == drawnVersion && generation == drawnGeneration &&
            styles === drawnStyles && composition == drawnComposition && rowsHidden === drawnHidden
        ) {
            return drawnLines
        }
        val texts = lineTexts(state, shown, styles, composition, rows)
        drawnLines = texts.mapIndexedNotNull { index, line ->
            if (line.isEmpty()) return@mapIndexedNotNull null
            val layout = measurer.measure(line, style = textStyle, softWrap = false)
            val width = layout.size.width.toFloat()
            if (width > widestDrawnLine) widestDrawnLine = width
            val row = shown.first + index
            DrawnLine(row, layout, Offset(0f, row * lineHeight + baseline - layout.firstBaseline))
        }
        drawnArea = shown
        drawnVersion = state.rawVersion
        drawnGeneration = generation
        drawnStyles = styles
        drawnComposition = composition
        drawnHidden = rowsHidden
        return drawnLines
    }

    /** @return the parts of the [line] that move on their own during a reflow, null if nothing is moving */
    fun movingParts(line: DrawnLine): List<MovingPart>? {
        val transition = activeReflow ?: return null
        val row = line.row
        val y = row * lineHeight
        return transition.partsOf(row) { rowParts(transition, row, line.layout) }.map { part ->
            MovingPart(part.left, part.right, distanceOf(transition, part.start, part.left, y))
        }
    }

    private fun rowParts(transition: ReflowTransition, row: Int, layout: TextLayoutResult): List<ReflowTransition.RowPart> {
        val start = rows.start(row)
        val inside = insideBreaks(transition, row)
        return (listOf(start) + inside).mapIndexed { index, from ->
            ReflowTransition.RowPart(
                start = from,
                left = layout.getHorizontalPosition(from - start, usePrimaryDirection = true),
                right = inside.getOrNull(index)
                    ?.let { layout.getHorizontalPosition(it - start, usePrimaryDirection = true) }
                    ?: layout.size.width.toFloat(),
            )
        }
    }

    /**
     * @param row the row
     * @param layout the layout of the whole row
     * @param position where to draw the row
     */
    class DrawnLine(val row: Int, val layout: TextLayoutResult, val position: Offset)

    /**
     * @param layout the layout of the text
     * @param position where to draw it
     */
    class TrailingText(val layout: TextLayoutResult, val position: Offset)

    /**
     * @param left where the part starts in the row
     * @param right where the part ends in the row
     * @param distance how far the part is away from its place
     */
    class MovingPart(val left: Float, val right: Float, val distance: Offset)
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

/**
 * What the overlay sees: the places of things as they are on the screen, also while they move during a reflow. Reading
 * them updates the overlay on each of its frames.
 */
internal class ShownEditorLayout(private val layout: EditorLineLayout) : KodeEditorLayout by layout {
    override fun getLineTop(line: Int) = layout.getLineTop(line) + layout.shownLineDistance(line)
    override fun getLineBottom(line: Int) = layout.getLineBottom(line) + layout.shownLineDistance(line)
    override fun getLineBaseline(line: Int) = layout.getLineBaseline(line) + layout.shownLineDistance(line)
    override fun getCursorRect(offset: Int) = layout.shownCursorRect(offset)
    override fun getPathForRange(start: Int, end: Int) = layout.shownPathForRange(start, end)
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
    override fun shownPosition(offset: Int, forward: Boolean) = layout.shownPosition(offset, forward)
}
