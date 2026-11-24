package de.markusressel.kodeeditor.library.compose.layout

import de.markusressel.kodeeditor.library.compose.animation.ReflowTransition
import de.markusressel.kodeeditor.library.compose.animation.TYPING_MAX_CHARS
import de.markusressel.kodeeditor.library.compose.animation.TYPING_WINDOW_MS
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.state.mapPosition

/**
 * Where the last changes of the writers ended, the newest first, with the transition of their last slide. A short
 * change right there is typed on (the typing slide, R6b of docs/reveal.md), see [TYPING_WINDOW_MS], so somebody who
 * types keeps their even speed while others write elsewhere.
 */
internal class TypingRuns {

    /**
     * The changes of one writer one behind the other.
     *
     * @property end where the cursor of the writer was after their last change, the end of an insertion or the start
     * of a removal
     * @property transition the slide of their last change
     */
    class Run {
        var end = -1
            internal set
        var transition: ReflowTransition? = null
    }

    private val runs = ArrayList<Run>()

    /** where the cursor was after the last change of anybody */
    var lastEnd = -1
        private set

    /**
     * @param splice a change of the text, the other runs move with it
     * @return the run that the change continues, null if it is no key right behind one, and the run of the change
     */
    fun onSplice(splice: TextSplice): Pair<Run?, Run> {
        val removed = splice.removedLength
        val inserted = splice.inserted.length
        val continued = runs.firstOrNull { run ->
            when {
                removed == 0 -> inserted <= TYPING_MAX_CHARS && splice.start == run.end
                inserted == 0 -> removed <= TYPING_MAX_CHARS && splice.start + removed == run.end
                else -> false
            }
        }
        runs.forEach { if (it !== continued) it.end = mapPosition(it.end, splice) }
        val current = continued ?: Run().also { runs.add(0, it) }
        current.end = splice.start + inserted
        if (runs.size > MAX_TYPING_RUNS) runs.removeAt(runs.lastIndex)
        lastEnd = current.end
        return continued to current
    }

    private companion object {
        /** how many writers keep typing on at once */
        const val MAX_TYPING_RUNS = 4
    }
}
