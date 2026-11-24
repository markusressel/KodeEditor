package de.markusressel.kodeeditor.library.compose.state

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
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAnimations
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAppearance
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorLocalAnimations

/**
 * Called on the main thread, within the call that made the change.
 */
interface KodeEditorListener {
    /**
     * The user changed the text, by the keyboard or an action of the editor like paste or undo.
     * Changes made with [KodeEditorState.applyRemote] and [KodeEditorState.setText] are not reported.
     */
    fun onLocalEdit(splice: TextSplice) {}

    /**
     * The user or the app moved the cursor or the selection, e.g. with a tap, the keyboard or
     * [KodeEditorState.setSelection]. Moves by changes of the text are not reported.
     */
    fun onLocalSelection(selection: TextRange) {}

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

    /** whether the text changes because the whole text was set, which nobody has to see as a movement */
    internal var replacingText = false
        private set

    /** whether the change that is being applied is a big change of the user, see [bigChange] */
    internal var applyingBigChange = false
        private set

    /** the selection when the last change of somebody else moved it there, null after the user moved it */
    internal var selectionMovedByOthers: TextRange? = null
        private set

    /** whether the change that is being applied came from somebody else, see [applyRemote] */
    internal var applyingRemote = false
        private set

    /** changes each time the whole text was set */
    internal var textReplacements by mutableIntStateOf(0)
        private set

    /** told right before each change of somebody else, see [applyRemote] */
    private val beforeRemoteObservers = mutableListOf<() -> Unit>()

    internal fun addBeforeRemoteObserver(observer: () -> Unit) {
        beforeRemoteObservers += observer
    }

    internal fun removeBeforeRemoteObserver(observer: () -> Unit) {
        beforeRemoteObservers -= observer
    }

    /** the text the last change removed, for the observers */
    internal var lastRemoved = ""
        private set

    /** told about each change of the text, by [SplicePhase], each editor that shows this state adds its own */
    private val spliceObservers = Array(SplicePhase.entries.size) { mutableListOf<(TextSplice) -> Unit>() }

    /** @param phase when it is told, all observers of a phase before those of the next one */
    internal fun addSpliceObserver(phase: SplicePhase, observer: (TextSplice) -> Unit) {
        spliceObservers[phase.ordinal] += observer
    }

    internal fun removeSpliceObserver(phase: SplicePhase, observer: (TextSplice) -> Unit) {
        spliceObservers[phase.ordinal] -= observer
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

    /** how text appears, of the editor that showed this state last, null while none shows it or it does not animate */
    internal var appearance: KodeEditorAppearance?
        get() = appearances.values.lastOrNull()
        set(value) = showAppearance(this, value)

    /** the appearance of each editor that shows this state, by the editor, in the order they came */
    private val appearances = LinkedHashMap<Any, KodeEditorAppearance>()

    /**
     * @param owner the editor that shows this state
     * @param appearance its appearance, null when it goes away or does not animate
     */
    internal fun showAppearance(owner: Any, appearance: KodeEditorAppearance?) {
        appearances.remove(owner)
        if (appearance != null) appearances[owner] = appearance
        if (appearances.isEmpty()) finishAppearing()
    }

    /** when the appearing ranges started and how long they take */
    private val appearTimings = AppearTimings()

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

    /** @return where the lines start now, a copy that stays the same */
    internal fun lineStarts(): IntArray = lines.starts()

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
            beforeRemoteObservers.toList().forEach { it() }
            applyingRemote = true
            try {
                replace(cleaned, selectionState.mapped(cleaned), compositionState?.mappedInside(cleaned))
            } finally {
                applyingRemote = false
            }
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
            val moved = range != selectionState
            selectionState = range
            selectionMovedByOthers = null
            compositionState = null
            changedInBatch = true
            if (moved) listeners.toList().forEach { it.onLocalSelection(range) }
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
        val tags = rangeAlphasState.mapTo(HashSet()) { it.tag }
        appearTimings.retain(tags)
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
    fun appear(start: Int, end: Int, tag: String = "") = appear(start, end, tag, null)

    /** @param durationMs how long it takes, null for the rate of the writer, see [KodeEditorAppearance] */
    private fun appear(start: Int, end: Int, tag: String, durationMs: Long?) {
        val config = appearance ?: return
        val from = start.coerceIn(0, currentText.length)
        val to = end.coerceIn(from, currentText.length)
        if (from == to) return
        val added = AnnotatedString.Range(0f, from, to, appearTimings.newTag(tag, durationMs))
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

    /** @return how long the appearing range with [tag] takes, null if it did not start yet */
    internal fun appearDurationOf(tag: String): Long? = appearTimings.durationOf(tag)

    /**
     * Moves the appearing text on by a frame. A range that is new gets its duration from the rate of its writer, see
     * [KodeEditorAppearance], and keeps it.
     *
     * @param nowMs the time of the frame
     * @param scale scales the durations, 0 shows all of it at once
     * @return whether text is still appearing
     */
    internal fun onAppearFrame(nowMs: Long, scale: Float = 1f): Boolean {
        val ranges = rangeAlphasState
        if (ranges.isEmpty()) return false
        if (scale <= 0f) {
            // animations were turned off while text appeared
            finishAppearing()
            return false
        }
        setRangeAlphas(appearTimings.onFrame(ranges, nowMs, scale, appearance ?: KodeEditorAppearance()))
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
    fun undo() = bigChange {
        batch {
            val before = undoStack.undo() ?: return@batch
            replace(before.splice, before.selection, null)
            listeners.toList().forEach { it.onLocalEdit(before.splice) }
            undoState++
        }
    }

    /** does the last undone change of the user again */
    fun redo() = bigChange {
        batch {
            val after = undoStack.redo() ?: return@batch
            replace(after.splice, after.selection, null)
            listeners.toList().forEach { it.onLocalEdit(after.splice) }
            undoState++
        }
    }

    /**
     * Runs [block] as a big change of the user, like paste, undo or a replacement of the search, which animates with
     * [KodeEditorLocalAnimations.bigChanges]
     */
    internal fun <T> bigChange(block: () -> T): T {
        if (applyingBigChange) return block()
        applyingBigChange = true
        try {
            return block()
        } finally {
            applyingBigChange = false
        }
    }

    /** a change from the keyboard, which knows about it already */
    internal fun editFromKeyboard(splice: TextSplice, selection: TextRange, composition: TextRange?) = batch {
        editFromUser(splice, selection, composition)
    }

    /** a selection and composition set by the keyboard itself, so it keeps composing */
    internal fun setSelectionFromKeyboard(selection: TextRange, composition: TextRange?) = batch {
        val range = selection.coercedIn(currentText.length)
        if (range != selectionState || composition != compositionState) {
            val moved = range != selectionState
            selectionState = range
            selectionMovedByOthers = null
            compositionState = composition
            changedInBatch = true
            if (moved) listeners.toList().forEach { it.onLocalSelection(range) }
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
            lastRemoved = old.substring(splice.start, splice.start + splice.removedLength)
            currentText = buildString(old.length - splice.removedLength + splice.inserted.length) {
                append(old, 0, splice.start)
                append(splice.inserted)
                append(old, splice.start + splice.removedLength, old.length)
            }
            lines.apply(old, splice)
            versionState.intValue++
            moveRangeStyles(splice)
            spliceObservers.forEach { phase -> phase.toList().forEach { it(splice) } }
            val local = appearance?.local
            if (applyingBigChange && !applyingRemote && local?.bigChanges == true && splice.inserted.isNotEmpty()) {
                val end = splice.start + splice.inserted.length
                appear(splice.start, end, LOCAL_APPEAR_TAG, local.appearMs.toLong())
            }
        }
        selectionState = selection.coercedIn(currentText.length)
        selectionMovedByOthers = if (applyingRemote) selectionState else null
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

/** in which order the observers of a [KodeEditorState] hear about a change of the text */
internal enum class SplicePhase {
    /** right after the range styles moved, e.g. a reveal, the search, the cursors of others */
    Listeners,

    /** the styles of the highlighting */
    Highlighting,

    /** the folds, which the rows need */
    Folds,

    /** the wrapped rows, and what moves with them */
    Rows,
}

/** how the state tells the connected keyboard about changes */
internal interface InputLink {
    /** the selection or composition changed, or the keyboard changed the text itself */
    fun update()

    /** the whole text changed, the keyboard can start over */
    fun restart()

    /** the keyboard wants to know the text around the cursor after each change, identified by [token] */
    fun monitorExtractedText(token: Int) {}
}
