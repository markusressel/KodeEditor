package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import de.markusressel.kodeeditor.library.compose.fold.CollapsedRanges
import de.markusressel.kodeeditor.library.compose.input.isWordPart
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState

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
 * @param rangeStyles the styles set from outside, which win over the others
 */
internal fun lineTexts(
    state: KodeEditorState,
    lines: IntRange,
    styles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
    composition: TextRange? = null,
    rows: EditorRows = UnwrappedRows(state),
    collapsed: CollapsedRanges? = null,
    placeholderStyle: SpanStyle = SpanStyle(),
    rangeStyles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
): List<AnnotatedString> {
    val count = lines.count()
    val first = rows.start(lines.first)
    val last = rows.end(lines.last)
    val spans = arrayOfNulls<MutableList<AnnotatedString.Range<SpanStyle>>>(count)
    val rangeSpans = if (rangeStyles.isEmpty()) spans else arrayOfNulls(count)

    fun add(style: SpanStyle, start: Int, end: Int, spans: Array<MutableList<AnnotatedString.Range<SpanStyle>>?>) {
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

    styles.forEach { add(it.item, it.start, it.end, spans) }
    composition?.let { add(COMPOSING_STYLE, it.min, it.max, spans) }
    rangeStyles.forEach { add(it.item, it.start, it.end, rangeSpans) }

    val text = state.text
    return List(count) { index ->
        val line = lines.first + index
        val start = rows.start(line)
        val end = rows.end(line)
        val folds = collapsed?.inRow(start, end)
        val outside = if (rangeSpans === spans) null else rangeSpans[index]
        if (folds == null) {
            val all = if (outside == null) spans[index].orEmpty() else spans[index].orEmpty() + outside
            AnnotatedString(text = text.substring(start, end), spanStyles = all)
        } else {
            fun shown(spans: List<AnnotatedString.Range<SpanStyle>>?) = spans.orEmpty().mapNotNull { span ->
                val from = folds.toShown(start + span.start)
                val to = folds.toShown(start + span.end)
                if (from < to) AnnotatedString.Range(span.item, from, to) else null
            }
            // these win over the styles of the text they replace, and only the styles from outside win over them
            val placeholders = (folds.first..folds.last).map { range ->
                val from = folds.placeholderStart(range)
                AnnotatedString.Range(placeholderStyle, from, from + collapsed.placeholders[range].length)
            }
            AnnotatedString(
                text = folds.shownText(text, end),
                spanStyles = shown(spans[index]) + placeholders + shown(outside),
            )
        }
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
