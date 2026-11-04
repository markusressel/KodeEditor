package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
 * @param animateFolds whether what is shown moves to its new place when lines fold or unfold
 * @param foldPlaceholderStyle the style of what is shown instead of a folded part of a line
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
    private val animateFolds: Boolean = false,
    private val foldPlaceholderStyle: SpanStyle = SpanStyle(),
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
                    val shownRanges = collapsed
                    reflow = ReflowTransition(state, shownFirstRows, breaks, reflow, lineHeight, { start, end ->
                        measureBetween(start, end, shownRanges)
                    })
                }
            },
            sameBreaks = it::breakAlike,
            hiddenLines = { folds?.hidden },
            collapsed = { collapsed },
            shownBreaker = it::breaksOfShown,
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

    /** what is shown as a placeholder at the moment */
    private val collapsed: CollapsedRanges? get() = folds?.inline?.collapsed

    /** the text between two positions of a row as it was shown, without styles, which is enough to know where it was */
    private fun measureBetween(start: Int, end: Int, ranges: CollapsedRanges?): Float {
        if (start == end) return 0f
        val visibleEnd = ranges?.outsideOf(end) ?: end
        val text = ranges?.inRow(start, visibleEnd)?.shownText(state.text, visibleEnd)
            ?: state.text.substring(start, visibleEnd)
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
            collapsed
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

    /** a fold moves the lines like a reflow does, this is what a reflow has no need for */
    private var foldFade: FoldFade? = null

    /** changes when a fold starts moving lines */
    var foldAnimations by mutableIntStateOf(0)
        private set

    private val activeFoldFade: FoldFade?
        get() {
            val transition = activeReflow ?: return null
            return foldFade?.takeIf { it.transition === transition }
        }

    init {
        if (animateFolds) folds?.onFoldChanged = ::onFoldChanged
        folds?.inline?.onChanged = ::onCollapsedChanged
    }

    /** the cursor or a change of the text made other parts of lines collapse or open */
    private fun onCollapsedChanged(before: CollapsedRanges?, after: CollapsedRanges?, animate: Boolean) {
        val changes = before?.changesTo(after) ?: after?.changesTo(null).orEmpty()
        val wrapped = wrappedRows
        wrapped?.update(wrapWidth())
        val lines = state.lineCount
        val firstRows = wrapped?.currentFirstRows ?: IntArray(lines + 1) { it }
        val breaks = wrapped?.currentBreaks ?: arrayOfNulls(lines)
        wrapped?.onCollapsedChanged(changes.mapTo(HashSet()) { state.lineOf(it.min) })
        if (wrapped == null) widestDrawnLine = 0f
        if (!animate || !animateFolds) return
        reflow = ReflowTransition(
            state = state,
            firstRows = folds?.hidden?.shownFirstRows(firstRows) ?: firstRows,
            breaks = breaks,
            previous = reflow?.takeIf { it.valid },
            lineHeight = lineHeight,
            measure = { start, end -> measureBetween(start, end, before) },
            changedFolds = changes,
        )
        foldAnimations++
    }

    private fun onFoldChanged(before: HiddenLines?, after: HiddenLines?) {
        val wrapped = wrappedRows
        wrapped?.update(wrapWidth())
        val base = wrapped ?: unwrappedRows
        val lines = state.lineCount
        val firstRows = wrapped?.currentFirstRows ?: IntArray(lines + 1) { it }
        val breaks = wrapped?.currentBreaks ?: arrayOfNulls(lines)
        val transition = ReflowTransition(
            state = state,
            firstRows = before?.shownFirstRows(firstRows) ?: firstRows,
            breaks = breaks,
            previous = reflow?.takeIf { it.valid },
            lineHeight = lineHeight,
            measure = { start, end -> measureBetween(start, end, collapsed) },
        )
        val rowsBefore = before?.let { FoldedRows(base, it) } ?: base
        val leaving = leavingRows(rowsBefore, before, after)
        rowsHidden = after
        foldedRows = null
        val rowsAfter = rows
        reflow = transition
        foldFade = FoldFade(
            transition = transition,
            appearing = before,
            staying = after,
            leaving = leaving.map { row ->
                // into the place below the line that hides them
                row.copy(targetTop = (rowsAfter.rowOf(state.lineStart(row.line)) + 1) * lineHeight)
            },
        )
        foldAnimations++
    }

    /** @return the rows in the drawn part of the document that a fold is about to hide */
    private fun leavingRows(rows: EditorRows, before: HiddenLines?, after: HiddenLines?): List<LeavingRow> {
        after ?: return emptyList()
        val area = window.value
        val lastRow = rows.count - 1
        val shown = (area.top / lineHeight).toInt().coerceIn(0, lastRow)..
            (area.bottom / lineHeight).toInt().coerceIn(0, lastRow)
        val hiddenRows = shown.filter { row ->
            val line = rows.lineOf(row)
            after.rangeOf(line) >= 0 && (before == null || before.rangeOf(line) < 0)
        }
        if (hiddenRows.isEmpty()) return emptyList()
        val texts = lineTexts(state, shown, currentStyles(), state.composition, rows, collapsed, foldPlaceholderStyle)
        return hiddenRows.map { row ->
            val line = rows.lineOf(row)
            val layout = measurer.measure(texts[row - shown.first], style = textStyle, softWrap = false)
            LeavingRow(
                line = line,
                firstOfLine = row == rows.firstRow(line),
                layout = layout,
                top = row * lineHeight,
                targetTop = row * lineHeight,
                boxes = collapsed?.let { placeholderBoxes(it, rows, row, layout, 0f) }.orEmpty(),
            )
        }
    }

    /**
     * @param row a row
     * @return how visible the row is while a fold moves lines
     */
    fun rowAlpha(row: Int) = lineAlpha(rows.lineOf(row))

    /**
     * @param line a line
     * @return how visible the line is while a fold moves lines
     */
    fun lineAlpha(line: Int): Float {
        val fade = activeFoldFade ?: return 1f
        return if (fade.isAppearing(line)) fade.transition.progress else 1f
    }

    /** the rows that a fold just hid, at the place and with the visibility they have at the moment */
    val shownLeavingRows: List<ShownLeavingRow>
        get() {
            val fade = activeFoldFade ?: return emptyList()
            val transition = fade.transition
            val remaining = 1f - transition.progress
            return fade.leaving.map { row ->
                val top = row.targetTop + (row.top + transition.scrolled - row.targetTop) * remaining
                ShownLeavingRow(row.line, row.firstOfLine, row.layout, top, top + baseline, remaining, row.boxes)
            }
        }

    private var rowsHidden: HiddenLines? = null
    private var foldedRows: FoldedRows? = null
    private var foldedGeneration = -1

    /** tells the rows about changes of the text */
    val rowObserver: ((TextSplice) -> Unit)? = wrappedRows?.let { rows ->
        { splice -> rows.onSplice(splice, settledWidth = if (state.replacingText) wrapWidth() else null) }
    }

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
            val longest = measureLine(longestShownLine()).size.width.toFloat()
            val width = maxOf(longest, widestDrawnLine)
            return IntSize(ceil(width).toInt(), height)
        }

    /** the line that shows the most characters */
    private fun longestShownLine(): Int {
        val ranges = collapsed ?: return state.longestLine()
        var best = 0
        var bestLength = -1
        var range = 0
        for (line in 0 until state.lineCount) {
            val start = state.lineStart(line)
            val end = state.lineEnd(line)
            var length = end - start
            while (range < ranges.size && ranges.starts[range] < end) {
                length -= ranges.ends[range] - ranges.starts[range] - ranges.placeholders[range].length
                range++
            }
            if (length > bestLength) {
                bestLength = length
                best = line
            }
        }
        return best
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
        val last = if (before > rows.start(row) && state.text[before].isLowSurrogate()) before - 1 else before
        return collapsed?.outsideOf(last) ?: last
    }

    private fun xOf(row: Int, offset: Int): Float {
        val start = rows.start(row)
        val inRow = inRow(row, offset.coerceIn(start, rows.end(row)))
        return measureRow(row).getHorizontalPosition(inRow, usePrimaryDirection = true)
    }

    /** what the [row] shows instead of parts of the text, null if it shows them all */
    private fun rowFolds(row: Int): RowFolds? {
        val ranges = collapsed ?: return null
        return ranges.inRow(rows.start(row), rows.end(row))
    }

    /** @return where the position in the text is in the layout of the [row] */
    private fun inRow(row: Int, offset: Int) = rowFolds(row)?.toShown(offset) ?: (offset - rows.start(row))

    /**
     * @param position a point
     * @return the part of the text whose placeholder is at the point, null if there is none
     */
    fun foldedRangeAt(position: Offset): TextRange? {
        val row = rowAt(position.y)
        val folds = rowFolds(row) ?: return null
        val ranges = collapsed ?: return null
        val layout = measureRow(row)
        for (index in folds.first..folds.last) {
            val start = folds.placeholderStart(index)
            val end = start + ranges.placeholders[index].length
            val left = layout.getHorizontalPosition(start, usePrimaryDirection = true)
            val right = layout.getHorizontalPosition(end, usePrimaryDirection = true)
            // a single character is hard to hit with a finger
            val slack = ((lineHeight - (right - left)) / 2).coerceAtLeast(0f)
            if (position.x >= left - slack && position.x <= right + slack) {
                return TextRange(ranges.starts[index], ranges.ends[index])
            }
        }
        return null
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

    /** @return how far the end of the [line] is away from its place on the screen during a reflow */
    fun shownLineBottomDistance(line: Int): Float {
        val transition = activeReflow ?: return 0f
        val lastRowTop = getLineBottom(line) - lineHeight
        return distanceOf(transition, getLineEnd(line), 0f, lastRowTop).y
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
        val lineStart = state.lineStart(line)
        val breaks = transition.breaksOf(line)?.map { lineStart + it }.orEmpty()
        val inside = (breaks + transition.changeEdgesWithin(start + 1, end)).filter { it in (start + 1) until end }
        // a part of the row that is folded differently than before has its edges in there too
        return inside.distinct().sorted()
    }

    override fun getOffsetForPosition(position: Offset): Int {
        val row = rowAt(position.y)
        val inRow = measureRow(row).getOffsetForPosition(Offset(position.x, lineHeight / 2))
        val offset = rowFolds(row)?.toText(inRow) ?: (rows.start(row) + inRow)
        return offset.coerceAtMost(lastPositionIn(row))
    }

    override fun getWordBoundary(offset: Int): TextRange {
        val word = wordAt(state.text, offset)
        return collapsed?.visiblePartOf(offset, word) ?: word
    }

    /** @param range a part of the text that the user wants to see */
    fun openFold(range: TextRange) {
        folds?.inline?.open(range)
    }

    override fun isLineHidden(line: Int): Boolean = folds?.isHidden(line) ?: false

    /** the next position from [offset] in the given direction that is not folded away */
    fun shownPosition(offset: Int, forward: Boolean): Int {
        val position = folds?.shownPosition(offset, forward) ?: offset
        return collapsed?.skip(position, forward) ?: position
    }

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
        val text = lineTexts(
            state, line..line, currentStyles(), state.composition, unwrappedRows, collapsed, foldPlaceholderStyle,
        ).single()
        return measurer.measure(text, style = textStyle, softWrap = false)
    }

    /** the styled single line layout of [row] */
    private fun measureRow(row: Int): TextLayoutResult {
        val text = lineTexts(
            state, row..row, currentStyles(), state.composition, rows, collapsed, foldPlaceholderStyle,
        ).single()
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
    private var drawnCollapsed: CollapsedRanges? = null
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
        val ranges = collapsed
        if (
            shown == drawnArea && state.rawVersion == drawnVersion && generation == drawnGeneration &&
            styles === drawnStyles && composition == drawnComposition && rowsHidden === drawnHidden &&
            ranges === drawnCollapsed
        ) {
            return drawnLines
        }
        val texts = lineTexts(state, shown, styles, composition, rows, ranges, foldPlaceholderStyle)
        drawnLines = texts.mapIndexedNotNull { index, line ->
            if (line.isEmpty()) return@mapIndexedNotNull null
            val layout = measurer.measure(line, style = textStyle, softWrap = false)
            val width = layout.size.width.toFloat()
            if (width > widestDrawnLine) widestDrawnLine = width
            val row = shown.first + index
            val position = Offset(0f, row * lineHeight + baseline - layout.firstBaseline)
            val top = row * lineHeight
            DrawnLine(row, layout, position, ranges?.let { placeholderBoxes(it, rows, row, layout, top) }.orEmpty())
        }
        drawnArea = shown
        drawnVersion = state.rawVersion
        drawnGeneration = generation
        drawnStyles = styles
        drawnComposition = composition
        drawnHidden = rowsHidden
        drawnCollapsed = ranges
        return drawnLines
    }

    /** @return where the placeholders of the [row] are */
    private fun placeholderBoxes(
        ranges: CollapsedRanges,
        rows: EditorRows,
        row: Int,
        layout: TextLayoutResult,
        top: Float,
    ): List<Rect> {
        val folds = ranges.inRow(rows.start(row), rows.end(row)) ?: return emptyList()
        return (folds.first..folds.last).map { index ->
            val start = folds.placeholderStart(index)
            val end = start + ranges.placeholders[index].length
            // apart from what is above and below, and with a bit of room for the characters next to it
            val inset = lineHeight * FOLD_BOX_INSET
            Rect(
                left = layout.getHorizontalPosition(start, usePrimaryDirection = true),
                top = top + inset,
                right = layout.getHorizontalPosition(end, usePrimaryDirection = true),
                bottom = top + lineHeight - inset,
            )
        }
    }

    /** @return the parts of the [line] that move on their own during a reflow, null if nothing is moving */
    fun movingParts(line: DrawnLine): List<MovingPart>? {
        val transition = activeReflow ?: return null
        val row = line.row
        val y = row * lineHeight
        val parts = transition.partsOf(row) { rowParts(transition, row, line.layout) }
        val distances = parts.map { distanceOf(transition, it.start, it.left, y) }
        return parts.mapIndexed { index, part ->
            val distance = distances[index]
            var right = part.right
            if (part.appearing) {
                // what follows moves out of the way, and what appears only takes the room it left so far
                val next = parts.getOrNull(index + 1)
                val nextDistance = distances.getOrNull(index + 1)
                if (next != null && nextDistance != null && nextDistance.y == distance.y) {
                    right = (next.left + nextDistance.x - distance.x).coerceIn(part.left, part.right)
                }
            }
            MovingPart(part.left, right, distance, if (part.appearing) transition.progress else 1f)
        }
    }

    private fun rowParts(transition: ReflowTransition, row: Int, layout: TextLayoutResult): List<ReflowTransition.RowPart> {
        val start = rows.start(row)
        val inside = insideBreaks(transition, row)
        return (listOf(start) + inside).mapIndexed { index, from ->
            ReflowTransition.RowPart(
                start = from,
                left = layout.getHorizontalPosition(inRow(row, from), usePrimaryDirection = true),
                right = inside.getOrNull(index)
                    ?.let { layout.getHorizontalPosition(inRow(row, it), usePrimaryDirection = true) }
                    ?: layout.size.width.toFloat(),
                appearing = transition.isChanged(from),
            )
        }
    }

    /**
     * @param row the row
     * @param layout the layout of the whole row
     * @param position where to draw the row
     * @param boxes where the placeholders of the row are
     */
    class DrawnLine(
        val row: Int,
        val layout: TextLayoutResult,
        val position: Offset,
        val boxes: List<Rect> = emptyList(),
    )

    /**
     * @param layout the layout of the text
     * @param position where to draw it
     */
    class TrailingText(val layout: TextLayoutResult, val position: Offset)

    /**
     * @param line the line the row is a part of
     * @param firstOfLine whether this is the first row of the line
     * @param layout the layout of the whole row
     * @param top the top of the row before the fold
     * @param targetTop the top of the row where it disappears
     * @param boxes where the placeholders of the row are, relative to its top
     */
    private data class LeavingRow(
        val line: Int,
        val firstOfLine: Boolean,
        val layout: TextLayoutResult,
        val top: Float,
        val targetTop: Float,
        val boxes: List<Rect>,
    )

    /**
     * What a fold needs on top of its [ReflowTransition]
     *
     * @param transition moves the lines
     * @param appearing the lines that were hidden
     * @param staying the lines that are hidden now
     * @param leaving the rows that are hidden now, and were drawn before
     */
    private class FoldFade(
        val transition: ReflowTransition,
        private val appearing: HiddenLines?,
        private val staying: HiddenLines?,
        val leaving: List<LeavingRow>,
    ) {
        fun isAppearing(line: Int) = appearing != null && appearing.rangeOf(line) >= 0 &&
            (staying == null || staying.rangeOf(line) < 0)
    }

    /**
     * @param line the line the row is a part of
     * @param firstOfLine whether this is the first row of the line
     * @param layout the layout of the whole row
     * @param top where the top of the row is at the moment
     * @param baseline where the baseline of the row is at the moment
     * @param alpha how visible the row is at the moment
     * @param boxes where the placeholders of the row are, relative to its top
     */
    class ShownLeavingRow(
        val line: Int,
        val firstOfLine: Boolean,
        val layout: TextLayoutResult,
        val top: Float,
        val baseline: Float,
        val alpha: Float,
        val boxes: List<Rect>,
    )

    /**
     * @param left where the part starts in the row
     * @param right where the part ends in the row
     * @param distance how far the part is away from its place
     * @param alpha how visible the part is
     */
    class MovingPart(val left: Float, val right: Float, val distance: Offset, val alpha: Float = 1f)
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
 * @param collapsed what is shown as a placeholder
 * @param placeholderStyle the style of the placeholders
 */
internal fun lineTexts(
    state: KodeEditorState,
    lines: IntRange,
    styles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    composition: TextRange? = null,
    rows: EditorRows = UnwrappedRows(state),
    collapsed: CollapsedRanges? = null,
    placeholderStyle: SpanStyle = SpanStyle(),
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
        val start = rows.start(line)
        val end = rows.end(line)
        val folds = collapsed?.inRow(start, end)
        if (folds == null) {
            AnnotatedString(text = text.substring(start, end), spanStyles = spans[index].orEmpty())
        } else {
            val shownSpans = spans[index].orEmpty().mapNotNull { span ->
                val from = folds.toShown(start + span.start)
                val to = folds.toShown(start + span.end)
                if (from < to) AnnotatedString.Range(span.item, from, to) else null
            }
            // these come last, so they win over the styles of the text they replace
            val placeholders = (folds.first..folds.last).map { range ->
                val from = folds.placeholderStart(range)
                AnnotatedString.Range(placeholderStyle, from, from + collapsed.placeholders[range].length)
            }
            AnnotatedString(text = folds.shownText(text, end), spanStyles = shownSpans + placeholders)
        }
    }
}

private val COMPOSING_STYLE = SpanStyle(textDecoration = TextDecoration.Underline)

/** how far the box behind a placeholder stays away from the edges of its row, as a part of the row height */
private const val FOLD_BOX_INSET = 0.04f

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
