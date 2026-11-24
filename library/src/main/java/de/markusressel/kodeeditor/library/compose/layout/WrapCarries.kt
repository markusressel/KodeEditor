package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import de.markusressel.kodeeditor.library.compose.animation.CubicEaseOut
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.state.mapPosition

/**
 * The word that hidden text is revealed into at the start of a wrapped row, drawn at the end of the row above while it
 * still fits there, like the word of somebody who types (the wrap carry, R8 of docs/reveal.md). A chunk keeps its room
 * from the start, so its rows are broken as if it was all there. Without this, the head of the reveal jumps to the next
 * row at the start of a word that does not fit, before any of the word is shown.
 *
 * - While the revealed part of the word fits behind the end of the row above, it is drawn there, and so is the cursor
 *   at its end.
 * - When it does not fit anymore, or the head leaves the word, it slides to its place in its row in the time of a slide
 *   of inserted text, with the cubic ease-out, and carries the cursor with it.
 * - After a revealed line break only the cursor glides that way, from the end of the line above to the start of the new
 *   one, and the text stays at its place.
 *
 * Only text that appears is carried, so text that was there before never moves. Without wrapping or without an
 * appearance nothing is carried.
 *
 * @param state the text, with the transparent range styles that hide text and the ranges that appear
 * @param lineHeight the height of a row
 * @param rows the rows of the text
 * @param xOf where a position of the text is in its row
 * @param wrapWidth the width the lines wrap at, null without wrapping
 * @param slideMs how long the slide of inserted text takes, 0 without
 */
internal class WrapCarries(
    private val state: KodeEditorState,
    private val lineHeight: Float,
    private val rows: () -> EditorRows,
    private val xOf: (row: Int, offset: Int) -> Float,
    private val wrapWidth: () -> Float?,
    private val slideMs: () -> Int,
) {
    /**
     * @param start where the word starts, at the start of its row
     * @param end where the carried part of the row ends
     * @param delta how far the word is away from its place while it is carried
     * @param caretOnly whether only the cursor glides, after a line break, where no word is carried
     */
    private class Entry(var start: Int, var end: Int, var delta: Offset, val caretOnly: Boolean = false) {
        var released = false
        var startMs = -1L
        var progress = 0f
        val done: Boolean get() = progress >= 1f
    }

    /**
     * @param row the row the part is in
     * @param start where it starts in the text
     * @param end where it ends in the text
     * @param distance how far it is drawn away from its place now
     * @param caretOnly whether only the cursor in it is drawn away, the text stays
     */
    class Carried(val row: Int, val start: Int, val end: Int, val distance: Offset, val caretOnly: Boolean)

    private var entries = HashMap<Int, Entry>()

    /** each frame of a slide, whoever draws a carried word reads it */
    private var frame by mutableLongStateOf(0L)

    private var cacheKey: List<Any?>? = null
    private var cached: List<Carried> = emptyList()

    /** whether a word slides to its place */
    val releasing: Boolean get() = entries.values.any { it.released && !it.done }

    /** @return the words that are drawn away from their place now */
    fun current(): List<Carried> {
        val width = wrapWidth()
        val rows = rows()
        val key = listOf(frame, state.version, state.rangeStyles, state.rangeAlphas, width, rows, slideMs())
        if (key == cacheKey) return cached
        cacheKey = key
        cached = update(width, rows)
        return cached
    }

    /** @return how far the text at [offset] is drawn away from its place */
    fun distanceAt(offset: Int): Offset {
        val carried = current()
        if (carried.isEmpty()) return Offset.Zero
        return carried.firstOrNull { offset >= it.start && offset <= it.end }?.distance ?: Offset.Zero
    }

    /** @param nowMs the time of the frame, moves the words that slide to their place */
    fun onFrame(nowMs: Long) {
        val duration = slideMs()
        var moved = false
        entries.values.forEach { entry ->
            if (!entry.released || entry.done) return@forEach
            if (entry.startMs < 0) entry.startMs = nowMs
            val time = if (duration <= 0) 1f else ((nowMs - entry.startMs) / duration.toFloat()).coerceIn(0f, 1f)
            entry.progress = CubicEaseOut.transform(time)
            moved = true
        }
        if (moved) frame = nowMs
    }

    /** the words keep their place in the text when it changes */
    val spliceObserver: (TextSplice) -> Unit = { splice ->
        val moved = HashMap<Int, Entry>()
        entries.values.forEach { entry ->
            entry.start = mapPosition(entry.start, splice)
            entry.end = mapPosition(entry.end, splice)
            moved[entry.start] = entry
        }
        entries = moved
    }

    private fun update(width: Float?, rows: EditorRows): List<Carried> {
        if (width == null || slideMs() <= 0) {
            entries.clear()
            return emptyList()
        }
        val alphas = state.rangeAlphas
        val text = state.rawText
        val seen = HashSet<Int>()
        state.rangeStyles.forEach { range ->
            if (!range.isHidden()) return@forEach
            val head = range.start
            if (head <= 0 || head > text.length) return@forEach
            val row = rows.rowOf(head)
            val start = rows.start(row)
            if (row == 0) return@forEach
            // a row that continues a wrapped line carries its word, the first row of a line only the cursor
            val lineBreak = start == state.lineStart(rows.lineOf(row))
            if (lineBreak && text[start - 1] != '\n') return@forEach
            if (!lineBreak && rows.lineOf(row - 1) != rows.lineOf(row)) return@forEach
            if ((start until head).any { text[it].isWhitespace() }) return@forEach
            // what was there before stays, only what was revealed just now is carried
            if (!alphas.appearing(if (head == start || lineBreak) start - 1 else start, head)) return@forEach
            val endAbove = xOf(row - 1, if (lineBreak) start - 1 else start)
            val startX = xOf(row, start)
            val fits = !lineBreak && endAbove + xOf(row, head) - startX <= width + FIT_SLACK
            val entry = entries.getOrPut(start) { Entry(start, head, Offset(endAbove - startX, -lineHeight), lineBreak) }
            seen += start
            entry.end = minOf(rows.end(row), range.end)
            if (!entry.released) {
                entry.delta = Offset(endAbove - startX, -lineHeight)
                if (!fits) entry.released = true
            }
        }
        val result = ArrayList<Carried>()
        entries.values.removeAll { entry ->
            // the head left the word, which goes to its place now
            if (entry.start !in seen) entry.released = true
            entry.done
        }
        entries.values.forEach { entry ->
            if (entry.start > text.length) return@forEach
            val remaining = 1f - entry.progress
            result += Carried(rows.rowOf(entry.start), entry.start, entry.end, entry.delta * remaining, entry.caretOnly)
        }
        return result
    }

    /** @return whether all of the text from [start] to [end] appears, or the character at [start] if they are equal */
    private fun List<AnnotatedString.Range<Float>>.appearing(start: Int, end: Int): Boolean {
        if (start < 0) return false
        val last = maxOf(end, start + 1)
        var position = start
        while (position < last) {
            val range = firstOrNull { it.item < 1f && it.start <= position && position < it.end } ?: return false
            position = range.end
        }
        return true
    }

    private fun AnnotatedString.Range<*>.isHidden(): Boolean {
        val style = item as? SpanStyle ?: return false
        return style.color == Color.Transparent && start < end
    }

    private companion object {
        /** pixels, a word that ends right at the wrap width still fits */
        const val FIT_SLACK = 0.5f
    }
}
