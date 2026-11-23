package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * One change of the text.
 *
 * @param start where the change happens
 * @param removedLength how many characters are removed at [start]
 * @param inserted the text that is inserted at [start]
 */
data class TextSplice(val start: Int, val removedLength: Int, val inserted: String)

/**
 * Called on the main thread, within the call that made the change.
 */
interface KodeEditorListener {
    /**
     * The user changed the text, by the keyboard or an action of the editor like paste or undo.
     * Changes made with [KodeEditorState.applyRemote] and [KodeEditorState.setText] are not reported.
     */
    fun onLocalEdit(splice: TextSplice) {}

    /** the selection or the composition changed, or the text */
    fun onStateChanged() {}
}

/**
 * The text of a [KodeEditor], and the only place it is kept in.
 *
 * The keyboard, the hardware keys and the touch gestures change this directly and synchronously, there is no second
 * copy of the text that could get out of date. So an application does not have to hand the text back to the editor,
 * it learns about each change as a [TextSplice] and can send its own changes with [applyRemote], in any order, without
 * losing what the user typed.
 *
 * All functions must be called on the main thread.
 */
@Stable
class KodeEditorState(
    initialText: String = "",
    initialSelection: TextRange = TextRange(0),
) {
    private var currentText = initialText
    private var lines = LineIndex(initialText)
    private val versionState = mutableIntStateOf(0)
    private var selectionState by mutableStateOf(initialSelection.coercedIn(initialText.length))
    private var compositionState by mutableStateOf<TextRange?>(null)
    private val undoStack = UndoStack()
    private var undoState by mutableIntStateOf(0)
    private val listeners = mutableListOf<KodeEditorListener>()
    private var rangeStylesState by mutableStateOf(emptyList<AnnotatedString.Range<SpanStyle>>())
    private var rangeAlphasState by mutableStateOf(emptyList<AnnotatedString.Range<Float>>())

    private var batchDepth = 0
    private var changedInBatch = false
    private var restartInBatch = false

    internal var inputLink: InputLink? = null

    /** told about every change of the text, wherever it came from */
    internal var spliceObserver: ((TextSplice) -> Unit)? = null

    /** whether the text changes because the whole text was set, which nobody has to see as a movement */
    internal var replacingText = false
        private set

    /** changes each time the whole text was set */
    internal var textReplacements by mutableIntStateOf(0)
        private set

    /** told about every change of the text too, for the folds */
    internal var foldObserver: ((TextSplice) -> Unit)? = null

    /** told about every change of the text too, for the wrapped rows */
    internal var rowObserver: ((TextSplice) -> Unit)? = null

    /** told about each change of the text, right after the range styles moved */
    private val spliceListeners = mutableListOf<(TextSplice) -> Unit>()

    internal fun addSpliceListener(listener: (TextSplice) -> Unit) {
        spliceListeners += listener
    }

    internal fun removeSpliceListener(listener: (TextSplice) -> Unit) {
        spliceListeners -= listener
    }

    /** the version without observing it, for code that is not a part of the UI */
    internal val rawVersion: Int get() = versionState.intValue

    /** the current text */
    val text: String
        get() {
            versionState.intValue
            return currentText
        }

    /** changes with each change of the text */
    val version: Int get() = versionState.intValue

    /** the current selection, its end is the cursor */
    val selection: TextRange get() = selectionState

    /** the range the keyboard is currently composing, usually the word being typed */
    val composition: TextRange? get() = compositionState

    /** the current text, selection and composition */
    val value: TextFieldValue get() = TextFieldValue(text, selection, composition)

    /**
     * Styles drawn over ranges of the text, set with [setRangeStyles]. They move with each change of the text, in the
     * same call that changes it.
     */
    val rangeStyles: List<AnnotatedString.Range<SpanStyle>> get() = rangeStylesState

    /**
     * How visible the ranges of the text are that are appearing, see [appear]. They move with each change of the text,
     * like [rangeStyles].
     */
    internal val rangeAlphas: List<AnnotatedString.Range<Float>> get() = rangeAlphasState

    /** how text appears, set by the editor that shows this state, null while none shows it or it does not animate */
    internal var appearance: KodeEditorAppearance? = null
        set(value) {
            field = value
            if (value == null) finishAppearing()
        }

    /** the time of the frame each appearing range started in, by its tag */
    private val appearStarts = HashMap<String, Long>()
    private var nextAppearId = 0

    val canUndo: Boolean
        get() {
            undoState
            return undoStack.canUndo
        }

    val canRedo: Boolean
        get() {
            undoState
            return undoStack.canRedo
        }

    internal val lineCount: Int
        get() {
            versionState.intValue
            return lines.count
        }

    /** the current text without observing it, for code that is not a part of the UI */
    internal val rawText: String get() = currentText

    internal fun lineStart(line: Int): Int {
        versionState.intValue
        return lines.start(line)
    }

    /** the end of the line, without its line break */
    internal fun lineEnd(line: Int): Int {
        versionState.intValue
        return if (line + 1 < lines.count) lines.start(line + 1) - 1 else currentText.length
    }

    internal fun lineOf(offset: Int): Int {
        versionState.intValue
        return lines.lineOf(offset.coerceIn(0, currentText.length))
    }

    /** the line with the most characters */
    internal fun longestLine(): Int {
        versionState.intValue
        return lines.longest(currentText.length)
    }

    fun addListener(listener: KodeEditorListener) {
        listeners += listener
    }

    fun removeListener(listener: KodeEditorListener) {
        listeners -= listener
    }

    /**
     * Applies changes that did not come from the user of this editor, e.g. from another user.
     *
     * The selection and the composition move with the text, and the keyboard is told about it. Text inserted at the
     * edge of the composition stays out of it, so the keyboard does not replace it with the next letter. Each splice
     * refers to the text after the one before it.
     *
     * @param splices the changes
     * @param appear whether the inserted text appears animated, see [appear]
     * @param appearTag tells the appearing text apart, see [finishAppearing]
     */
    fun applyRemote(splices: List<TextSplice>, appear: Boolean = false, appearTag: String = "") = batch {
        splices.forEach { splice ->
            val start = splice.start.coerceIn(0, currentText.length)
            val end = (start + splice.removedLength).coerceIn(start, currentText.length)
            val cleaned = TextSplice(start, end - start, splice.inserted)
            undoStack.onRemote(cleaned)
            undoState++
            // The keyboard only hears where things are now. Starting it over would end what it does at the moment, and
            // what it sends in that moment would get lost.
            replace(cleaned, selectionState.mapped(cleaned), compositionState?.mappedInside(cleaned))
            if (appear) appear(start, start + cleaned.inserted.length, appearTag)
        }
    }

    /**
     * Replaces the whole text, only the part that differs is changed.
     *
     * @param text the new text
     * @param selection the new selection, by default the current one moves with the text
     */
    fun setText(text: String, selection: TextRange? = null) = batch {
        val splice = diff(currentText, text)
        if (splice != null) {
            undoStack.clear()
            undoState++
            val mapped = selectionState.mapped(splice)
            replacingText = true
            try {
                replace(splice, selection?.coercedIn(text.length) ?: mapped, null)
            } finally {
                replacingText = false
            }
            textReplacements++
            restartInBatch = true
        } else if (selection != null) {
            setSelection(selection)
        }
    }

    /** moves the cursor or the selection, the keyboard stops composing */
    fun setSelection(selection: TextRange) = batch {
        val range = selection.coercedIn(currentText.length)
        if (range != selectionState || compositionState != null) {
            selectionState = range
            compositionState = null
            changedInBatch = true
        }
    }

    /**
     * Styles ranges of the text from outside, e.g. to hide a part with a transparent color until it is revealed. The
     * text, the selection and the keyboard stay as they are, and copying copies the real text. These styles win over
     * the highlighting and the underline of the composition. Changing them only redraws the lines that are shown.
     * Use styles that keep the size of the text, like a color or a background: the positions of the cursor and of
     * touches are measured without these styles.
     *
     * Each range moves with the changes of the text, in the same call that changes it: text inserted in front of it
     * moves it, text inserted inside of it becomes a part of it, text inserted right at its start or its end stays out
     * of it, and removed text shrinks it. A range that ends up empty is removed. They can be set at any time on the
     * main thread, also from a [KodeEditorListener], and the next frame shows them.
     *
     * @param ranges the styles with their ranges in UTF-16 characters of [text], like [String] indices, the start is
     * included and the end is not. The tag is not used, it can tell the ranges apart. Ranges are cut to the text,
     * empty ones are left out
     */
    fun setRangeStyles(ranges: List<AnnotatedString.Range<SpanStyle>>) {
        rangeStylesState = ranges.cutTo(currentText.length)
    }

    /**
     * Draws ranges of the text with an alpha. Each range costs a layer of its own on each row it is in.
     *
     * @param ranges how visible each range is, from 0 for invisible to 1. Ranges with an alpha of 1 or more are drawn
     * as usual
     */
    internal fun setRangeAlphas(ranges: List<AnnotatedString.Range<Float>>) {
        rangeAlphasState = ranges.cutTo(currentText.length)
        appearStarts.keys.retainAll(rangeAlphasState.mapTo(HashSet()) { it.tag })
    }

    /**
     * Lets a range of the text appear animated, as set with the appearance of [KodeEditorAnimations], e.g. text that
     * somebody else writes, or each piece of text that is revealed from under [setRangeStyles]. It starts with the next
     * frame. Nothing happens while no editor shows this state or its appearance is off. Text that is still hidden by a
     * transparent range style stays hidden while it appears. The range moves with the changes of the text like the
     * [rangeStyles]. Changes of the user do not end it, the application decides with [finishAppearing].
     *
     * @param start the start of the range, in UTF-16 characters of [text]
     * @param end the end of the range, not included
     * @param tag tells the appearing text apart, e.g. who wrote it, see [finishAppearing]
     */
    fun appear(start: Int, end: Int, tag: String = "") {
        val config = appearance ?: return
        val from = start.coerceIn(0, currentText.length)
        val to = end.coerceIn(from, currentText.length)
        if (from == to) return
        val added = AnnotatedString.Range(0f, from, to, "${nextAppearId++}$APPEAR_TAG_SEPARATOR$tag")
        setRangeAlphas(limitAppearing(rangeAlphasState, added, config))
    }

    /**
     * Shows text that is appearing at once, e.g. when the application stops following somebody else.
     *
     * @param tag only the text with this tag of [appear], null for all of it
     */
    fun finishAppearing(tag: String? = null) {
        val ranges = rangeAlphasState
        if (ranges.isEmpty()) return
        setRangeAlphas(if (tag == null) emptyList() else ranges.filter { it.tag.appearTag() != tag })
    }

    /**
     * Moves the appearing text on by a frame.
     *
     * @param nowMs the time of the frame
     * @param durationMs how long text takes to appear, 0 shows it at once
     * @return whether text is still appearing
     */
    internal fun onAppearFrame(nowMs: Long, durationMs: Long): Boolean {
        val ranges = rangeAlphasState
        if (ranges.isEmpty()) return false
        setRangeAlphas(
            ranges.mapNotNull { range ->
                val started = appearStarts.getOrPut(range.tag) { nowMs }
                val progress = if (durationMs > 0) (nowMs - started).toFloat() / durationMs else 1f
                if (progress >= 1f) return@mapNotNull null
                // fast at first, so new text does not stay invisible for long
                val remaining = 1f - progress.coerceAtLeast(0f)
                AnnotatedString.Range(1f - remaining * remaining, range.start, range.end, range.tag)
            },
        )
        return rangeAlphasState.isNotEmpty()
    }

    /** selects the whole text */
    fun selectAll() = setSelection(TextRange(0, currentText.length))

    /** replaces the selection with [text], and places the cursor behind it */
    fun replaceSelection(text: String) = localEdit(selectionState.min, selectionState.max, text)

    /**
     * Changes the text as if the user did it, e.g. for an action of the application: it can be undone, and is reported
     * to the listeners. Only the part that differs is changed.
     *
     * @param text the new text
     * @param selection the new selection
     */
    fun edit(text: String, selection: TextRange) = batch {
        val splice = diff(currentText, text)
        if (splice != null) {
            editFromUser(splice, selection.coercedIn(text.length), null)
            restartInBatch = true
        } else {
            setSelection(selection)
        }
    }

    /**
     * A change of the user that does not come through the keyboard, like a key of a hardware keyboard or paste. The
     * keyboard learns the new selection. It is not started over, as that would end a key repeat, which arrives as key
     * events one by one.
     */
    internal fun localEdit(start: Int, end: Int, text: String, cursor: TextRange = TextRange(start + text.length)) =
        batch {
            editFromUser(TextSplice(start, end - start, text), cursor, null)
        }

    /** takes back the last change of the user */
    fun undo() = batch {
        val before = undoStack.undo() ?: return@batch
        replace(before.splice, before.selection, null)
        listeners.toList().forEach { it.onLocalEdit(before.splice) }
        undoState++
    }

    /** does the last undone change of the user again */
    fun redo() = batch {
        val after = undoStack.redo() ?: return@batch
        replace(after.splice, after.selection, null)
        listeners.toList().forEach { it.onLocalEdit(after.splice) }
        undoState++
    }

    /** a change from the keyboard, which knows about it already */
    internal fun editFromKeyboard(splice: TextSplice, selection: TextRange, composition: TextRange?) = batch {
        editFromUser(splice, selection, composition)
    }

    /** a selection and composition set by the keyboard itself, so it keeps composing */
    internal fun setSelectionFromKeyboard(selection: TextRange, composition: TextRange?) = batch {
        val range = selection.coercedIn(currentText.length)
        if (range != selectionState || composition != compositionState) {
            selectionState = range
            compositionState = composition
            changedInBatch = true
        }
    }

    private fun editFromUser(splice: TextSplice, selection: TextRange, composition: TextRange?) {
        if (splice.removedLength == 0 && splice.inserted.isEmpty()) {
            setSelectionFromKeyboard(selection, composition)
            return
        }
        val removed = currentText.substring(splice.start, splice.start + splice.removedLength)
        val before = selectionState
        replace(splice, selection, composition)
        undoStack.record(splice, removed, before, selectionState)
        undoState++
        listeners.toList().forEach { it.onLocalEdit(splice) }
    }

    internal fun beginBatch() {
        batchDepth++
    }

    internal fun endBatch() {
        if (batchDepth == 0) return
        batchDepth--
        if (batchDepth > 0) return
        val changed = changedInBatch
        val restart = restartInBatch
        changedInBatch = false
        restartInBatch = false
        if (changed) {
            if (restart) inputLink?.restart() else inputLink?.update()
            listeners.toList().forEach { it.onStateChanged() }
        }
    }

    internal inline fun batch(block: () -> Unit) {
        beginBatch()
        try {
            block()
        } finally {
            endBatch()
        }
    }

    private fun replace(splice: TextSplice, selection: TextRange, composition: TextRange?) {
        if (splice.removedLength != 0 || splice.inserted.isNotEmpty()) {
            val old = currentText
            currentText = buildString(old.length - splice.removedLength + splice.inserted.length) {
                append(old, 0, splice.start)
                append(splice.inserted)
                append(old, splice.start + splice.removedLength, old.length)
            }
            lines.apply(old, splice)
            versionState.intValue++
            moveRangeStyles(splice)
            spliceListeners.toList().forEach { it(splice) }
            spliceObserver?.invoke(splice)
            foldObserver?.invoke(splice)
            rowObserver?.invoke(splice)
        }
        selectionState = selection.coercedIn(currentText.length)
        compositionState = composition?.takeIf { !it.collapsed && it.max <= currentText.length }
        changedInBatch = true
    }

    private fun moveRangeStyles(splice: TextSplice) {
        rangeStylesState = rangeStylesState.movedInside(splice)
        rangeAlphasState = rangeAlphasState.movedInside(splice)
    }

    private companion object {
        fun diff(old: String, new: String): TextSplice? {
            if (old == new) return null
            val limit = minOf(old.length, new.length)
            var prefix = 0
            while (prefix < limit && old[prefix] == new[prefix]) prefix++
            var suffix = 0
            while (suffix < limit - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
            return TextSplice(prefix, old.length - prefix - suffix, new.substring(prefix, new.length - suffix))
        }
    }
}

/**
 * @param initialText the text the editor starts with
 * @param initialSelection the selection the editor starts with
 * @return a [KodeEditorState] that is kept across recompositions
 */
@Composable
fun rememberKodeEditorState(
    initialText: String = "",
    initialSelection: TextRange = TextRange(0),
): KodeEditorState = remember { KodeEditorState(initialText, initialSelection) }

/** how the state tells the connected keyboard about changes */
internal interface InputLink {
    /** the selection or composition changed, or the keyboard changed the text itself */
    fun update()

    /** the whole text changed, the keyboard can start over */
    fun restart()

    /** the keyboard wants to know the text around the cursor after each change, identified by [token] */
    fun monitorExtractedText(token: Int) {}
}

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

/**
 * The appearing ranges with [added], without those that have to be shown at once now: of text that appears piece after
 * piece with the same tag, only the last [KodeEditorAppearance.window] characters appear, and there are at most
 * [KodeEditorAppearance.maxRanges].
 */
internal fun limitAppearing(
    ranges: List<AnnotatedString.Range<Float>>,
    added: AnnotatedString.Range<Float>,
    config: KodeEditorAppearance,
): List<AnnotatedString.Range<Float>> {
    val finished = HashSet<AnnotatedString.Range<Float>>()
    // the pieces of the same writer in front of the new one, the newest first
    val tag = added.tag.appearTag()
    var chainStart = added.start
    var length = added.end - added.start
    for (range in ranges.asReversed()) {
        if (range.end != chainStart || range.tag.appearTag() != tag) continue
        length += range.end - range.start
        chainStart = range.start
        if (length > config.window) finished += range
    }
    val kept = ranges.filter { it !in finished } + added
    return kept.takeLast(config.maxRanges.coerceAtLeast(1))
}

private const val APPEAR_TAG_SEPARATOR = '/'

/** the tag the application gave to [KodeEditorState.appear], behind the id that makes each range unique */
internal fun String.appearTag() = substringAfter(APPEAR_TAG_SEPARATOR)

/** the ranges cut to a text of [length], without the empty ones */
private fun <T> List<AnnotatedString.Range<T>>.cutTo(length: Int) = mapNotNull { range ->
    val start = range.start.coerceIn(0, length)
    val end = range.end.coerceIn(start, length)
    when {
        start == end -> null
        start == range.start && end == range.end -> range
        else -> AnnotatedString.Range(range.item, start, end, range.tag)
    }
}

/** each range moved by [movedInside], the same list if none of them moved */
private fun <T> List<AnnotatedString.Range<T>>.movedInside(splice: TextSplice): List<AnnotatedString.Range<T>> {
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

/**
 * Where the lines of the text start. A change only touches the lines it is in, and moves the ones after it.
 */
internal class LineIndex(text: String) {
    private var starts = IntArray(16)

    var count = 0
        private set

    init {
        add(0)
        var index = text.indexOf('\n')
        while (index >= 0) {
            add(index + 1)
            index = text.indexOf('\n', index + 1)
        }
    }

    private fun add(start: Int) {
        if (count == starts.size) starts = starts.copyOf(count * 2)
        starts[count++] = start
    }

    fun start(line: Int): Int = starts[line.coerceIn(0, count - 1)]

    fun lineOf(offset: Int): Int {
        var low = 0
        var high = count - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (starts[middle] <= offset) low = middle else high = middle - 1
        }
        return low
    }

    fun longest(textLength: Int): Int {
        var best = 0
        var bestLength = -1
        for (line in 0 until count) {
            val end = if (line + 1 < count) starts[line + 1] - 1 else textLength
            val length = end - starts[line]
            if (length > bestLength) {
                bestLength = length
                best = line
            }
        }
        return best
    }

    /**
     * @param old the text before the change
     * @param splice the change
     */
    fun apply(old: String, splice: TextSplice) {
        val delta = splice.inserted.length - splice.removedLength
        val removedEnd = splice.start + splice.removedLength
        val firstLine = lineOf(splice.start)
        var removedLines = 0
        for (index in splice.start until removedEnd) if (old[index] == '\n') removedLines++
        var insertedLines = 0
        for (char in splice.inserted) if (char == '\n') insertedLines++

        val newCount = count - removedLines + insertedLines
        val result = if (newCount > starts.size) IntArray(newCount * 2) else starts
        // the part that moves, handled first so it is not overwritten when the array is reused
        val tailFrom = firstLine + 1 + removedLines
        val tailTo = firstLine + 1 + insertedLines
        System.arraycopy(starts, tailFrom, result, tailTo, count - tailFrom)
        if (result !== starts) System.arraycopy(starts, 0, result, 0, firstLine + 1)
        for (line in tailTo until newCount) result[line] += delta
        var line = firstLine + 1
        splice.inserted.forEachIndexed { index, char ->
            if (char == '\n') result[line++] = splice.start + index + 1
        }
        starts = result
        count = newCount
    }
}

/**
 * The changes of the user that can be taken back. Typing is grouped into one step, until there is a pause.
 *
 * @param now the current time in milliseconds
 */
internal class UndoStack(private val now: () -> Long = System::currentTimeMillis) {

    private class Entry(
        var start: Int,
        var removed: String,
        var inserted: String,
        val selectionBefore: TextRange,
        var selectionAfter: TextRange,
        var time: Long,
    )

    class Step(val splice: TextSplice, val selection: TextRange)

    private val undo = ArrayDeque<Entry>()
    private val redo = ArrayDeque<Entry>()

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun record(splice: TextSplice, removed: String, selectionBefore: TextRange, selectionAfter: TextRange) {
        redo.clear()
        val time = now()
        val last = undo.lastOrNull()
        if (last != null && time - last.time < MERGE_WINDOW_MS && merge(last, splice, removed)) {
            last.selectionAfter = selectionAfter
            last.time = time
            return
        }
        undo.addLast(Entry(splice.start, removed, splice.inserted, selectionBefore, selectionAfter, time))
        if (undo.size > MAX_ENTRIES) undo.removeFirst()
    }

    private fun merge(last: Entry, splice: TextSplice, removed: String): Boolean {
        val lastEnd = last.start + last.inserted.length
        return when {
            // the keyboard replaces the word it is composing
            splice.start == last.start && splice.removedLength == last.inserted.length -> {
                last.inserted = splice.inserted
                true
            }

            // typing further, a new line starts a new step
            removed.isEmpty() && splice.start == lastEnd && '\n' !in splice.inserted -> {
                last.inserted += splice.inserted
                true
            }

            // deleting what was just typed
            splice.inserted.isEmpty() && splice.start + splice.removedLength == lastEnd &&
                splice.start >= last.start -> {
                last.inserted = last.inserted.substring(0, splice.start - last.start)
                true
            }

            else -> false
        }
    }

    fun undo(): Step? {
        val entry = undo.removeLastOrNull() ?: return null
        redo.addLast(entry)
        return Step(TextSplice(entry.start, entry.inserted.length, entry.removed), entry.selectionBefore)
    }

    fun redo(): Step? {
        val entry = redo.removeLastOrNull() ?: return null
        undo.addLast(entry)
        return Step(TextSplice(entry.start, entry.removed.length, entry.inserted), entry.selectionAfter)
    }

    /**
     * Steps that overlap the change can not be taken back anymore, and neither can the ones before them.
     * The others move with the text.
     */
    fun onRemote(splice: TextSplice) {
        redo.clear()
        var start = splice.start
        var index = undo.size - 1
        while (index >= 0) {
            val entry = undo[index]
            val entryEnd = entry.start + entry.inserted.length
            val delta = splice.inserted.length - splice.removedLength
            when {
                start + splice.removedLength <= entry.start -> entry.start += delta
                start >= entryEnd -> start += entry.removed.length - entry.inserted.length
                else -> {
                    repeat(index + 1) { undo.removeFirst() }
                    return
                }
            }
            index--
        }
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    private companion object {
        const val MERGE_WINDOW_MS = 1000L
        const val MAX_ENTRIES = 1000
    }
}
