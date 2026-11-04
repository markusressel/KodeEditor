package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange

/**
 * The lines that folds hide, as sorted ranges that neither overlap nor touch each other. The line in front of a range
 * is always visible.
 */
internal class HiddenLines(val firstLines: IntArray, val lastLines: IntArray) {
    /** @return the range that hides the [line], -1 if it is visible */
    fun rangeOf(line: Int): Int {
        var low = 0
        var high = firstLines.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                line < firstLines[middle] -> high = middle - 1
                line > lastLines[middle] -> low = middle + 1
                else -> return middle
            }
        }
        return -1
    }

    /**
     * @param firstRows the first row of each line, and the number of rows at the end
     * @return the same for what is shown, where a hidden line starts at the next row that is shown
     */
    fun shownFirstRows(firstRows: IntArray): IntArray {
        val shown = firstRows.copyOf()
        var removed = 0
        var range = 0
        for (line in firstRows.indices) {
            if (range < firstLines.size && line == lastLines[range] + 1) {
                removed += firstRows[line] - firstRows[firstLines[range]]
                range++
            }
            val inRange = range < firstLines.size && line >= firstLines[range]
            shown[line] = (if (inRange) firstRows[firstLines[range]] else firstRows[line]) - removed
        }
        return shown
    }
}

/**
 * The rows of [base] without those of the [hidden] lines. A position in a hidden line belongs to the last row in front
 * of it, so everything that asks for one gets a place that is shown.
 */
internal class FoldedRows(private val base: EditorRows, private val hidden: HiddenLines) : EditorRows {
    /** the row of [base] where each range starts */
    private val rangeStarts = IntArray(hidden.firstLines.size) { base.firstRow(hidden.firstLines[it]) }

    /** the rows that are hidden up to and including each range */
    private val hiddenUpTo = IntArray(rangeStarts.size)

    /** the row of this where each range would start */
    private val shownStarts = IntArray(rangeStarts.size)

    init {
        var sum = 0
        for (range in rangeStarts.indices) {
            shownStarts[range] = rangeStarts[range] - sum
            sum += base.lastRow(hidden.lastLines[range]) + 1 - rangeStarts[range]
            hiddenUpTo[range] = sum
        }
    }

    override val count = base.count - (hiddenUpTo.lastOrNull() ?: 0)

    /** @return the last index where [values] is not above [value], -1 if there is none */
    private fun lastNotAbove(values: IntArray, value: Int): Int {
        var low = 0
        var high = values.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (values[middle] <= value) low = middle + 1 else high = middle - 1
        }
        return high
    }

    private fun toBase(row: Int): Int {
        val range = lastNotAbove(shownStarts, row)
        return if (range < 0) row else row + hiddenUpTo[range]
    }

    private fun toShown(baseRow: Int): Int {
        val range = lastNotAbove(rangeStarts, baseRow)
        if (range < 0) return baseRow
        val shown = baseRow - hiddenUpTo[range]
        return if (shown < shownStarts[range]) shownStarts[range] - 1 else shown
    }

    override fun rowOf(offset: Int) = toShown(base.rowOf(offset))
    override fun lineOf(row: Int) = base.lineOf(toBase(row))
    override fun firstRow(line: Int) = toShown(base.firstRow(line))
    override fun lastRow(line: Int) = toShown(base.lastRow(line))
    override fun start(row: Int) = base.start(toBase(row))
    override fun end(row: Int) = base.end(toBase(row))
}

/**
 * What can be folded in the text of a [KodeEditorState], and what is folded.
 *
 * Folds are kept as positions in the text that move with each change, so they stay on their lines. A fold goes away
 * when its line can not be folded anymore, when a change crosses its edge, and when the cursor ends up in it. Changes
 * completely within a fold, e.g. of other users, leave it alone.
 */
internal class EditorFolds(
    private val state: KodeEditorState,
    private val provider: KodeEditorFoldProvider,
) : KodeEditorListener {

    private class Fold(var header: Int, var hiddenStart: Int, var hiddenEnd: Int)

    /** what is folded within lines */
    val inline = EditorInlineFolds(state, provider)

    // typing within a line does not change this, so what only shows the regions is not drawn again for it
    private val regions = derivedStateOf(structuralEqualityPolicy()) {
        state.version
        val lastLine = state.lineCount - 1
        provider.foldRegions(state.rawText)
            .filter { it.startLine in 0..lastLine && it.endLine > it.startLine }
            .map { if (it.endLine > lastLine) it.copy(endLine = lastLine) else it }
            .filter { it.endLine > it.startLine }
            .sortedBy { it.startLine }
    }

    private val folds = ArrayList<Fold>()
    private var foldedLines = emptySet<Int>()
    private var foldVersion by mutableIntStateOf(0)

    private var builtTextVersion = -1
    private var builtFoldVersion = -1
    private var builtHidden: HiddenLines? = null

    /** called when folds changed while the text stayed the same, with the hidden lines before and after that */
    var onFoldChanged: ((before: HiddenLines?, after: HiddenLines?) -> Unit)? = null

    /** the lines that are hidden, null if there are none */
    val hidden: HiddenLines?
        get() {
            val version = foldVersion
            if (folds.isEmpty()) return null
            val textVersion = state.version
            if (version != builtFoldVersion || textVersion != builtTextVersion) rebuild(version, textVersion)
            return builtHidden
        }

    private fun rebuild(version: Int, textVersion: Int) {
        val found = regions.value
        val lines = HashSet<Int>()
        folds.removeAll { fold ->
            val line = state.lineOf(fold.header)
            val region = regionAt(found, line)
            // two folds that ended up on one line are one
            if (region == null || !lines.add(line)) return@removeAll true
            fold.header = state.lineStart(line)
            fold.hiddenStart = state.lineStart(line + 1)
            fold.hiddenEnd = state.lineEnd(region.endLine)
            false
        }
        foldedLines = lines

        val firstLines = ArrayList<Int>()
        val lastLines = ArrayList<Int>()
        lines.sorted().forEach { line ->
            val first = line + 1
            val last = regionAt(found, line)!!.endLine
            if (firstLines.isNotEmpty() && first <= lastLines.last() + 1) {
                lastLines[lastLines.size - 1] = maxOf(lastLines.last(), last)
            } else {
                firstLines += first
                lastLines += last
            }
        }
        builtHidden = if (firstLines.isEmpty()) null else HiddenLines(firstLines.toIntArray(), lastLines.toIntArray())
        builtFoldVersion = version
        builtTextVersion = textVersion
    }

    private fun regionAt(found: List<FoldRegion>, line: Int): FoldRegion? {
        val index = found.binarySearchBy(line) { it.startLine }
        return if (index >= 0) found[index] else null
    }

    /** @return whether the [line] can be folded */
    fun canFold(line: Int) = regionAt(regions.value, line) != null

    /** @return whether the [line] hides the lines below it */
    fun isFolded(line: Int): Boolean {
        hidden
        return folds.isNotEmpty() && line in foldedLines
    }

    /** @return whether the [line] is hidden by a fold */
    fun isHidden(line: Int) = hidden?.let { it.rangeOf(line) >= 0 } ?: false

    /**
     * Folds or unfolds the [line]. A cursor or selection that would end up hidden moves to the end of the line.
     *
     * @return false if the line can not be folded
     */
    fun toggle(line: Int): Boolean {
        if (!canFold(line)) return false
        val before = hidden
        if (isFolded(line)) {
            val start = state.lineStart(line)
            folds.removeAll { it.header == start }
            foldVersion++
            onFoldChanged?.invoke(before, hidden)
            return true
        }
        folds += Fold(state.lineStart(line), 0, 0)
        foldVersion++
        val selection = state.selection
        val lines = hidden
        onFoldChanged?.invoke(before, lines)
        if (lines != null) {
            val selected = state.lineOf(selection.min)..state.lineOf(selection.max)
            if (selected.first in lines || selected.last in lines) state.setSelection(TextRange(state.lineEnd(line)))
        }
        return true
    }

    private operator fun HiddenLines.contains(line: Int) = rangeOf(line) >= 0

    /**
     * @param offset where the cursor would move to
     * @param forward the direction it moves in
     * @return the next position in that direction that is shown
     */
    fun shownPosition(offset: Int, forward: Boolean): Int {
        val lines = hidden ?: return offset
        val range = lines.rangeOf(state.lineOf(offset))
        if (range < 0) return offset
        val end = state.lineEnd(lines.lastLines[range])
        return if (forward && end < state.rawText.length) end + 1 else state.lineEnd(lines.firstLines[range] - 1)
    }

    /** the text changed, the positions of the folds still are those in front of the change */
    val spliceObserver: (TextSplice) -> Unit = { splice ->
        inline.onSplice(splice)
        val start = splice.start
        val end = splice.start + splice.removedLength
        val removed = folds.removeAll { fold ->
            val crosses = splice.removedLength > 0 && start <= fold.hiddenEnd && end >= fold.hiddenStart &&
                !(start >= fold.hiddenStart && end <= fold.hiddenEnd)
            if (!crosses) {
                fold.header = mapPosition(fold.header, splice)
                fold.hiddenStart = mapPosition(fold.hiddenStart, splice)
                fold.hiddenEnd = mapPosition(fold.hiddenEnd, splice)
            }
            crosses
        }
        if (removed) foldVersion++
    }

    override fun onStateChanged() {
        inline.onStateChanged()
        if (folds.isEmpty()) return
        val selection = state.selection
        if (!selection.collapsed) return
        val sameText = builtTextVersion == state.rawVersion
        val before = hidden
        val cursor = selection.end
        if (folds.removeAll { cursor in it.hiddenStart..it.hiddenEnd }) {
            foldVersion++
            if (sameText) onFoldChanged?.invoke(before, hidden)
        }
    }
}

/**
 * The arrows that fold and unfold what is next to them.
 *
 * @param folds what can be folded
 * @param width the width of this column in pixels at zoom 1
 * @param color the color of the arrows
 */
internal class FoldColumn(
    private val folds: EditorFolds,
    private val width: Int,
    private val color: Color,
) : KodeEditorGutterColumn {
    override fun width(lineCount: Int) = width

    override fun DrawScope.drawCell(cell: GutterCell) {
        if (!folds.canFold(cell.line)) return
        val center = Offset(cell.width / 2, cell.baseline - cell.width * ARROW_RISE)
        val size = cell.width * ARROW_SIZE
        val path = Path().apply {
            if (folds.isFolded(cell.line)) {
                moveTo(center.x - size / 2, center.y - size)
                lineTo(center.x + size / 2, center.y)
                lineTo(center.x - size / 2, center.y + size)
            } else {
                moveTo(center.x - size, center.y - size / 2)
                lineTo(center.x + size, center.y - size / 2)
                lineTo(center.x, center.y + size / 2)
            }
            close()
        }
        drawPath(path, color)
    }

    override fun onTap(line: Int) = folds.toggle(line)

    private companion object {
        const val ARROW_SIZE = 0.2f
        const val ARROW_RISE = 0.25f
    }
}

/**
 * Shows that a line hides the lines below it, like most editors do: a background across the line, and a placeholder
 * behind its text that unfolds it.
 *
 * @param folds what is folded
 * @param backgroundColor the color behind the line
 * @param placeholderColor the color of the placeholder
 */
internal class FoldedLineDecoration(
    private val folds: EditorFolds,
    private val backgroundColor: Color,
    placeholderColor: Color,
) : KodeEditorLineDecoration {
    private val placeholder = AnnotatedString(PLACEHOLDER, SpanStyle(color = placeholderColor))

    override fun DrawScope.drawBackground(line: DecoratedLine) {
        if (folds.isFolded(line.line)) drawRect(backgroundColor, size = Size(line.width, line.height))
    }

    override fun trailingText(line: Int) = placeholder.takeIf { folds.isFolded(line) }

    override fun onTrailingTextTap(line: Int) = folds.toggle(line)

    private companion object {
        const val PLACEHOLDER = " ⋯"
    }
}
