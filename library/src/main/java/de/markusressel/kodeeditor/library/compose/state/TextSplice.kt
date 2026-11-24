package de.markusressel.kodeeditor.library.compose.state

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange

/**
 * One change of the text.
 *
 * @param start where the change happens
 * @param removedLength how many characters are removed at [start]
 * @param inserted the text that is inserted at [start]
 */
data class TextSplice(val start: Int, val removedLength: Int, val inserted: String)

internal fun TextRange.coercedIn(length: Int) = TextRange(start.coerceIn(0, length), end.coerceIn(0, length))

/** where a range is after the change, it does not grow when text is inserted right at its edges */
internal fun TextRange.mapped(splice: TextSplice) = TextRange(mapPosition(start, splice), mapPosition(end, splice))

/** like [mapped], but text inserted at either edge stays out of the range */
internal fun TextRange.mappedInside(splice: TextSplice): TextRange? {
    val isInsertion = splice.removedLength == 0
    val first = if (isInsertion && min == splice.start) min + splice.inserted.length else mapPosition(min, splice)
    val last = mapPosition(max, splice)
    return if (first < last) TextRange(first, last) else null
}

/** the ranges cut to a text of [length], without the empty ones */
internal fun <T> List<AnnotatedString.Range<T>>.cutTo(length: Int) = mapNotNull { range ->
    val start = range.start.coerceIn(0, length)
    val end = range.end.coerceIn(start, length)
    when {
        start == end -> null
        start == range.start && end == range.end -> range
        else -> AnnotatedString.Range(range.item, start, end, range.tag)
    }
}

/** each range moved by [movedInside], the same list if none of them moved */
internal fun <T> List<AnnotatedString.Range<T>>.movedInside(splice: TextSplice): List<AnnotatedString.Range<T>> {
    if (isEmpty()) return this
    var changed = false
    val moved = mapNotNull { range -> range.movedInside(splice).also { if (it !== range) changed = true } }
    return if (changed) moved else this
}

/** like [mappedInside], the same object if it does not move, null if it ends up empty */
internal fun <T> AnnotatedString.Range<T>.movedInside(splice: TextSplice): AnnotatedString.Range<T>? {
    // in front of the change, also when text is inserted right at its end
    if (end <= splice.start) return this
    val moved = TextRange(start, end).mappedInside(splice) ?: return null
    return if (moved.min == start && moved.max == end) this else AnnotatedString.Range(item, moved.min, moved.max, tag)
}

internal fun mapPosition(position: Int, splice: TextSplice): Int {
    val end = splice.start + splice.removedLength
    return when {
        position <= splice.start -> position
        position >= end -> position - splice.removedLength + splice.inserted.length
        else -> splice.start
    }
}
