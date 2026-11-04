package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * The parts of the text that are shown as a placeholder, sorted and without overlaps. Each one stays within its line.
 */
internal class CollapsedRanges(val starts: IntArray, val ends: IntArray, val placeholders: Array<String>) {
    val size get() = starts.size

    /** @return the index of the first range that starts at [position] or behind it */
    private fun firstFrom(position: Int): Int {
        var low = 0
        var high = starts.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (starts[middle] < position) low = middle + 1 else high = middle
        }
        return low
    }

    /**
     * @param start where the row starts in the text
     * @param end where the row ends in the text
     * @return what the row looks like with its placeholders, null if it has none
     */
    fun inRow(start: Int, end: Int): RowFolds? {
        val first = firstFrom(start)
        if (first == starts.size || ends[first] > end) return null
        var last = first
        while (last + 1 < starts.size && ends[last + 1] <= end) last++
        return RowFolds(this, start, first, last)
    }

    /** @return the start of the range that has [offset] strictly within it, otherwise the [offset] */
    fun outsideOf(offset: Int): Int {
        val index = firstFrom(offset) - 1
        return if (index >= 0 && ends[index] > offset) starts[index] else offset
    }

    /**
     * @param offset a position
     * @param forward the direction the cursor moves in
     * @return the edge of the range in that direction if the position is strictly within one, otherwise the position
     */
    fun skip(offset: Int, forward: Boolean): Int {
        val index = firstFrom(offset) - 1
        if (index < 0 || ends[index] <= offset) return offset
        return if (forward) ends[index] else starts[index]
    }

    /**
     * @param offset where a word was looked for, outside of the ranges
     * @param word the word at that position
     * @return the part of the word that is shown, without what is hidden in front of or behind the position
     */
    fun visiblePartOf(offset: Int, word: TextRange): TextRange {
        var start = word.min
        var end = word.max
        val next = firstFrom(offset)
        if (next < size && starts[next] < end) end = starts[next]
        val previous = next - 1
        if (previous >= 0 && ends[previous] <= offset && ends[previous] > start) start = ends[previous]
        return if (start < end) TextRange(start, end) else TextRange(offset)
    }

    /** @return what is only in one of the two, as ranges of the text, sorted by their start */
    fun changesTo(other: CollapsedRanges?): List<TextRange> {
        other ?: return List(size) { TextRange(starts[it], ends[it]) }
        val result = ArrayList<TextRange>()
        var mine = 0
        var theirs = 0
        while (mine < size || theirs < other.size) {
            val same = mine < size && theirs < other.size && starts[mine] == other.starts[theirs] &&
                ends[mine] == other.ends[theirs] && placeholders[mine] == other.placeholders[theirs]
            when {
                same -> {
                    mine++
                    theirs++
                }

                theirs == other.size || (mine < size && starts[mine] <= other.starts[theirs]) -> {
                    result += TextRange(starts[mine], ends[mine])
                    mine++
                }

                else -> {
                    result += TextRange(other.starts[theirs], other.ends[theirs])
                    theirs++
                }
            }
        }
        return result
    }

    fun contentEquals(other: CollapsedRanges) = starts.contentEquals(other.starts) && ends.contentEquals(other.ends) &&
        placeholders.contentEquals(other.placeholders)
}

/**
 * Translates between positions in the text and in what a row shows, which are the same without placeholders.
 *
 * @param rowStart where the row starts in the text
 * @param first the first range of the row
 * @param last the last range of the row
 */
internal class RowFolds(
    private val ranges: CollapsedRanges,
    private val rowStart: Int,
    val first: Int,
    val last: Int,
) {
    /** @return the position in what the row shows, a position within a range is the start of its placeholder */
    fun toShown(offset: Int): Int {
        var removed = 0
        for (index in first..last) {
            val start = ranges.starts[index]
            if (offset <= start) break
            val end = ranges.ends[index]
            if (offset < end) return start - rowStart - removed
            removed += end - start - ranges.placeholders[index].length
        }
        return offset - rowStart - removed
    }

    /** @return the position in the text, a position within a placeholder is the closer one of its edges */
    fun toText(shown: Int): Int {
        var removed = 0
        for (index in first..last) {
            val start = ranges.starts[index]
            val shownStart = start - rowStart - removed
            if (shown <= shownStart) break
            val length = ranges.placeholders[index].length
            val end = ranges.ends[index]
            if (shown < shownStart + length) return if ((shown - shownStart) * 2 < length) start else end
            removed += end - start - length
        }
        return rowStart + shown + removed
    }

    /** @return the part of the row that is shown */
    fun shownText(text: String, rowEnd: Int): String = buildShown(text, rowEnd) { it }

    /** like [shownText], with placeholders that do not break */
    fun unbreakableText(text: String, rowEnd: Int): String = buildShown(text, rowEnd) { placeholder ->
        placeholder.replace(' ', NO_BREAK_SPACE).replace('-', NO_BREAK_HYPHEN)
    }

    private inline fun buildShown(text: String, rowEnd: Int, placeholder: (String) -> String) = buildString {
        var position = rowStart
        for (index in first..last) {
            append(text, position, ranges.starts[index])
            append(placeholder(ranges.placeholders[index]))
            position = ranges.ends[index]
        }
        append(text, position, rowEnd)
    }

    /** @return where the placeholder of the range [index] is in what the row shows */
    fun placeholderStart(index: Int) = toShown(ranges.starts[index])

    /**
     * @param breaks where the shown text of a whole line breaks
     * @return where the line breaks relative to its start. A placeholder moves to the next row as a whole
     */
    fun breaksInText(breaks: IntArray, rowEnd: Int): IntArray? {
        val result = IntArray(breaks.size)
        var size = 0
        var previous = rowStart
        for (shown in breaks) {
            val placeholder = placeholderAt(shown)
            var position = if (placeholder >= 0) ranges.starts[placeholder] else toText(shown)
            if (position <= previous && placeholder >= 0) position = ranges.ends[placeholder]
            if (position <= previous || position >= rowEnd) continue
            result[size++] = position - rowStart
            previous = position
        }
        return if (size == 0) null else result.copyOf(size)
    }

    /** @return the range whose placeholder has [shown] strictly within it, -1 if there is none */
    private fun placeholderAt(shown: Int): Int {
        var removed = 0
        for (index in first..last) {
            val shownStart = ranges.starts[index] - rowStart - removed
            val length = ranges.placeholders[index].length
            if (shown <= shownStart) return -1
            if (shown < shownStart + length) return index
            removed += ranges.ends[index] - ranges.starts[index] - length
        }
        return -1
    }

    private companion object {
        const val NO_BREAK_SPACE = '\u00A0'
        const val NO_BREAK_HYPHEN = '\u2011'
    }
}

/**
 * What the provider found and which of it is collapsed.
 *
 * Everything is collapsed, with a few exceptions. A fold opens when the user taps it, when the selection ends within it,
 * and when a change happens at its edge. It stays open while the cursor is at it, so the cursor can sit next to a fold
 * without opening it, but can also leave an open one through its edges. What the user just typed stays open until the
 * cursor leaves the line, the text would jump around while typing otherwise.
 *
 * The provider runs on another thread, until its result is there the folds move with the text.
 */
internal class EditorInlineFolds(
    private val state: KodeEditorState,
    private val provider: KodeEditorFoldProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var found = emptyList<InlineFold>()
    private var scannedVersion = -1

    /** the folds that are open because of the user, they go when the cursor is not at them anymore */
    private var opened = emptyList<TextRange>()

    /** the folds that stay open until the cursor leaves their line */
    private val heldOpen = ArrayList<TextRange>()

    /** whether the text changed since the state was looked at the last time */
    private var edited = false

    /** only changes when something else is collapsed, so moving the cursor does not draw the text again */
    var collapsed: CollapsedRanges? by mutableStateOf(null, neverEqualPolicy())
        private set

    /**
     * Told when other parts of the text are collapsed, with the ranges before and after that.
     * [animate] tells whether this is something to show as a movement
     */
    var onChanged: ((before: CollapsedRanges?, after: CollapsedRanges?, animate: Boolean) -> Unit)? = null

    init {
        found = foldsOf(state.rawText)
        scannedVersion = state.rawVersion
        collapsed = rangesOf(found.filter { !isOpen(it, state.selection) })
    }

    /** runs the provider whenever the text changed, until it is cancelled */
    suspend fun keepUpdated() {
        snapshotFlow { state.version }.collectLatest { version ->
            if (version == scannedVersion) return@collectLatest
            val text = state.rawText
            val result = withContext(dispatcher) { foldsOf(text) }
            // the text may have changed while the provider was running, and the next run is on its way then
            if (state.rawVersion == version) onScanned(result)
        }
    }

    /** runs the provider right here */
    fun scanNow() = onScanned(foldsOf(state.rawText))

    private fun onScanned(result: List<InlineFold>) {
        val before = found
        found = result
        scannedVersion = state.rawVersion
        val line = state.lineOf(state.selection.end)
        result.forEach { fold ->
            val isNew = before.none { it.start < fold.end && it.end > fold.start }
            if (isNew && state.lineOf(fold.start) == line) heldOpen += TextRange(fold.start, fold.end)
        }
        update(animate = false)
    }

    private fun foldsOf(text: String): List<InlineFold> {
        var end = 0
        return provider.inlineFolds(text)
            .filter { fold ->
                fold.start >= 0 && fold.end <= text.length && fold.start < fold.end &&
                    '\n' !in fold.placeholder && !splitsCharacter(text, fold.start) && !splitsCharacter(text, fold.end) &&
                    text.indexOf('\n', fold.start).let { it < 0 || it >= fold.end }
            }
            .sortedWith(compareBy<InlineFold> { it.start }.thenByDescending { it.end })
            .filter { fold -> (fold.start >= end).also { if (it) end = fold.end } }
    }

    private fun splitsCharacter(text: String, position: Int) =
        position in 1 until text.length && text[position - 1].isHighSurrogate() && text[position].isLowSurrogate()

    private fun rangesOf(folds: List<InlineFold>) = if (folds.isEmpty()) null else CollapsedRanges(
        starts = IntArray(folds.size) { folds[it].start },
        ends = IntArray(folds.size) { folds[it].end },
        placeholders = Array(folds.size) { folds[it].placeholder },
    )

    private fun InlineFold.overlaps(range: TextRange) = range.min <= end && range.max >= start

    /** A keyboard marks the word at the cursor on its own, which opens nothing until it actually changes the word */
    private fun isOpen(fold: InlineFold, selection: TextRange): Boolean {
        val first = selection.min
        val last = selection.max
        // a handle has to be shown, while a selection can cover a whole fold
        if (first in fold.start + 1 until fold.end || last in fold.start + 1 until fold.end) return true
        if (heldOpen.any { fold.overlaps(it) }) return true
        val atFold = first in fold.start..fold.end || last in fold.start..fold.end
        return atFold && opened.any { fold.overlaps(it) }
    }

    /** the text changed, the provider looks at it again when the whole change is done */
    fun onSplice(splice: TextSplice) {
        edited = true
        val spliceEnd = splice.start + splice.removedLength
        // the selection is still the one from before, a change there comes from the user
        val selection = state.selection
        if (selection.min <= spliceEnd && selection.max >= splice.start) {
            found.forEach { if (it.overlaps(TextRange(splice.start, spliceEnd))) heldOpen += TextRange(it.start, it.end) }
        }
        found = found.mapNotNull { it.movedBy(splice) }
        opened = opened.map { it.mappedBy(splice) }
        heldOpen.replaceAll { it.mappedBy(splice) }
        val current = collapsed ?: return
        val moved = ArrayList<InlineFold>(current.size)
        for (index in 0 until current.size) {
            InlineFold(current.starts[index], current.ends[index], current.placeholders[index])
                .movedBy(splice)?.let { moved += it }
        }
        collapsed = rangesOf(moved)
    }

    private fun TextRange.mappedBy(splice: TextSplice) = TextRange(mapPosition(min, splice), mapPosition(max, splice))

    private fun InlineFold.movedBy(splice: TextSplice): InlineFold? {
        val spliceEnd = splice.start + splice.removedLength
        return when {
            splice.start >= end -> this
            spliceEnd <= start -> {
                val distance = splice.inserted.length - splice.removedLength
                copy(start = start + distance, end = end + distance)
            }

            else -> null
        }
    }

    /** the selection, the composition or the text changed */
    fun onStateChanged() {
        update(animate = !edited)
    }

    /** @param range the fold the user wants to see, which stays open while the cursor is at it */
    fun open(range: TextRange) {
        opened = opened + range
        update(animate = true)
    }

    private fun update(animate: Boolean) {
        edited = false
        val length = state.rawText.length
        val line = state.lineOf(state.selection.end)
        heldOpen.removeAll { it.min > length || state.lineOf(it.min) != line }
        val selection = state.selection
        val open = found.filter { isOpen(it, selection) }
        opened = open.map { TextRange(it.start, it.end) }
        val before = collapsed
        val after = rangesOf(found.filter { it !in open })
        if (before === after || (before != null && after != null && before.contentEquals(after))) return
        collapsed = after
        onChanged?.invoke(before, after, animate)
    }
}
