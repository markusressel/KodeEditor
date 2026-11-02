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
 * All lines have the same height and only the lines that are asked for are measured, so this works at any length of
 * the text. A style with a larger font size does not make its line taller.
 *
 * Reading from this in a composable or in a draw phase updates it when the text changes.
 */
@Stable
interface KodeEditorLayout {
    /** the text this describes */
    val text: String

    /** the number of lines */
    val lineCount: Int

    /** the height of each line */
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
     * @return the top of the line
     */
    fun getLineTop(line: Int): Float

    /**
     * @param line a line
     * @return the bottom of the line
     */
    fun getLineBottom(line: Int): Float

    /**
     * @param line a line
     * @return the baseline of the line
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
 */
internal class EditorLineLayout(
    private val state: KodeEditorState,
    private val measurer: TextMeasurer,
    private val textStyle: TextStyle,
    private val highlighting: EditorHighlighting,
    private val window: State<IntRect>,
) : KodeEditorLayout {

    private val reference = measurer.measure("X", style = textStyle, softWrap = false)
    override val lineHeight = reference.size.height.toFloat().coerceAtLeast(1f)
    private val baseline = reference.firstBaseline

    private val lineCountState = derivedStateOf { state.lineCount }

    /** lines with few wide characters can be wider than the one with the most characters */
    private var widestDrawnLine by mutableFloatStateOf(0f)

    override val text: String get() = state.text

    override val lineCount: Int get() = lineCountState.value

    override val contentSize: IntSize
        get() {
            val longest = measureLine(state.longestLine()).size.width.toFloat()
            val width = maxOf(longest, widestDrawnLine)
            return IntSize(ceil(width).toInt(), (lineCount * lineHeight).toInt())
        }

    override fun getLineForOffset(offset: Int) = state.lineOf(offset)

    override fun getLineForVerticalPosition(y: Float) = (y / lineHeight).toInt().coerceIn(0, lineCount - 1)

    override fun getLineStart(line: Int) = state.lineStart(line)

    override fun getLineEnd(line: Int) = state.lineEnd(line)

    override fun getLineTop(line: Int) = line * lineHeight

    override fun getLineBottom(line: Int) = (line + 1) * lineHeight

    override fun getLineBaseline(line: Int) = line * lineHeight + baseline

    override fun getCursorRect(offset: Int): Rect {
        val line = state.lineOf(offset)
        val x = xOf(line, offset)
        return Rect(x, getLineTop(line), x, getLineBottom(line))
    }

    private fun xOf(line: Int, offset: Int): Float {
        val inLine = offset.coerceIn(state.lineStart(line), state.lineEnd(line)) - state.lineStart(line)
        return measureLine(line).getHorizontalPosition(inLine, usePrimaryDirection = true)
    }

    override fun getPathForRange(start: Int, end: Int): Path {
        val path = Path()
        val first = minOf(start, end).coerceIn(0, state.text.length)
        val last = maxOf(start, end).coerceIn(0, state.text.length)
        if (first == last) return path
        val area = window.value
        val lines = maxOf(state.lineOf(first), getLineForVerticalPosition(area.top.toFloat()))..
            minOf(state.lineOf(last), getLineForVerticalPosition(area.bottom.toFloat()))
        for (line in lines) {
            val lineStart = state.lineStart(line)
            val lineEnd = state.lineEnd(line)
            val left = if (first > lineStart) xOf(line, first) else xOf(line, lineStart)
            // a selected line break is shown as a bit of space, so an empty line can be seen as selected
            val right = if (last > lineEnd) xOf(line, lineEnd) + lineHeight / 4 else xOf(line, last)
            path.addRect(Rect(minOf(left, right), getLineTop(line), maxOf(left, right), getLineBottom(line)))
        }
        return path
    }

    override fun getOffsetForPosition(position: Offset): Int {
        val line = getLineForVerticalPosition(position.y)
        val inLine = measureLine(line).getOffsetForPosition(Offset(position.x, lineHeight / 2))
        return (state.lineStart(line) + inLine).coerceAtMost(state.lineEnd(line))
    }

    override fun getWordBoundary(offset: Int): TextRange = wordAt(state.text, offset)

    /** the styled single line layout of [line] */
    private fun measureLine(line: Int): TextLayoutResult {
        val text = lineTexts(state, line..line, currentStyles(), state.composition).single()
        return measurer.measure(text, style = textStyle, softWrap = false)
    }

    private fun currentStyles() = highlighting.styles

    /**
     * @param area the part of the document to draw
     * @return the lines within the area, each with its layout and where to draw it
     */
    fun linesWithin(area: IntRect): List<DrawnLine> {
        val lines = getLineForVerticalPosition(area.top.toFloat())..getLineForVerticalPosition(area.bottom.toFloat())
        val texts = lineTexts(state, lines, currentStyles(), state.composition)
        return texts.mapIndexedNotNull { index, line ->
            if (line.isEmpty()) return@mapIndexedNotNull null
            val layout = measurer.measure(line, style = textStyle, softWrap = false)
            val width = layout.size.width.toFloat()
            if (width > widestDrawnLine) widestDrawnLine = width
            DrawnLine(layout, Offset(0f, getLineBaseline(lines.first + index) - layout.firstBaseline))
        }
    }

    class DrawnLine(val layout: TextLayoutResult, val position: Offset)
}

/**
 * The text and the styles of each of the given [lines].
 *
 * Styles are not sorted by line, so this looks at each one once, for all lines together.
 *
 * @param state the text
 * @param lines the lines to get
 * @param styles the styles of the whole text
 * @param composition the text the keyboard is composing, which gets underlined
 */
internal fun lineTexts(
    state: KodeEditorState,
    lines: IntRange,
    styles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    composition: TextRange? = null,
): List<AnnotatedString> {
    val count = lines.count()
    val first = state.lineStart(lines.first)
    val last = state.lineEnd(lines.last)
    val spans = arrayOfNulls<MutableList<AnnotatedString.Range<SpanStyle>>>(count)

    fun add(style: SpanStyle, start: Int, end: Int) {
        if (end <= first || start >= last) return
        val startLine = state.lineOf(start.coerceAtLeast(first)) - lines.first
        val endLine = state.lineOf((end - 1).coerceAtMost(last - 1)) - lines.first
        for (index in startLine.coerceAtLeast(0)..endLine.coerceAtMost(count - 1)) {
            val line = lines.first + index
            val lineStart = state.lineStart(line)
            val from = start.coerceAtLeast(lineStart) - lineStart
            val to = end.coerceAtMost(state.lineEnd(line)) - lineStart
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
            text = text.substring(state.lineStart(line), state.lineEnd(line)),
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

/** moving the cursor with the keys uses the same lines the editor shows */
internal class EditorTextLines(private val layout: KodeEditorLayout) : TextLines {
    override val lineCount get() = layout.lineCount
    override fun lineOf(offset: Int) = layout.getLineForOffset(offset)
    override fun lineAt(y: Float) = layout.getLineForVerticalPosition(y)
    override fun topOf(line: Int) = layout.getLineTop(line)
    override fun xOf(offset: Int) = layout.getCursorRect(offset).left
    override fun offsetAt(line: Int, x: Float) =
        layout.getOffsetForPosition(Offset(x, layout.getLineTop(line) + layout.lineHeight / 2))
}
