package de.markusressel.kodeeditor.library.compose.state

import androidx.compose.ui.text.TextRange

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
