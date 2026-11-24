package de.markusressel.kodeeditor.library.compose.animation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.cursor.KodeEditorCursorGlide
import de.markusressel.kodeeditor.library.compose.cursor.KodeEditorRemoteCursor
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState
import kotlin.math.roundToInt

/**
 * What the [KodeEditor] animates.
 *
 * @param gutterWidth whether the bar next to the text animates to a new width
 * @param lineNumbers whether new lines fade in the bar, and the numbers of removed lines fade out. Other columns only
 * know the lines of the document, so they have nothing to show for removed lines
 * @param reflow whether the text, the bar, the selection, the cursor and the overlay move to their new place when lines
 * wrap differently, which only applies with line wrapping
 * @param folding whether what is below a fold moves to its new place, and the lines of the fold fade with their columns
 * and decorations, when it folds or unfolds
 * @param scrolling whether the view can move to where [KodeEditorViewState] scrolls or zooms to, instead of jumping
 * there
 * @param appearance how text appears that the application marks with [KodeEditorState.appear] or
 * [KodeEditorState.applyRemote], how the changes of others and the cursors of other users move, and how big changes of
 * the user animate, see [KodeEditorAppearance]. null shows all of it at once. The typing of the user never animates
 */
@Immutable
data class KodeEditorAnimations(
    val gutterWidth: Boolean = true,
    val lineNumbers: Boolean = true,
    val reflow: Boolean = true,
    val folding: Boolean = true,
    val scrolling: Boolean = true,
    val appearance: KodeEditorAppearance? = KodeEditorAppearance(),
) {
    companion object {
        /** Everything jumps to its new place at once */
        val None = KodeEditorAnimations(
            gutterWidth = false,
            lineNumbers = false,
            reflow = false,
            folding = false,
            scrolling = false,
            appearance = null,
        )
    }
}

/**
 * How text that the application marks appears, e.g. text that somebody else writes. It fades in, and can rise into its
 * place while it does. This only changes how the text is drawn, nothing is measured again. Text that somebody else
 * inserts with [KodeEditorState.applyRemote] takes its room at once, also while it is still hidden, and the text behind
 * it slides out of the way, into other rows too, like the cursors glide (see [KodeEditorCursorGlide.durationMs]). When
 * they remove text, the text behind it slides back into the gap. Keys and backspaces one right after the other slide at
 * an even speed, with the cursor of whoever types them. The
 * system setting that scales the duration of animations scales this too, and turns it off with a scale of 0.
 *
 * How long a piece takes to appear depends on how fast its writer writes: each writer has a smoothed rate of the
 * characters that appear for them, an exponential average over about a second. A piece gets its duration when it starts
 * to appear and keeps it, from [durationMs] for text that comes at [slowCharsPerSecond] or slower, like typing, to
 * [maxDurationMs] at [fastCharsPerSecond] or faster, like an agent that writes a lot at once, in a straight line in
 * between. So a lot of text at once is smoothed more, and slow text still shows soon.
 *
 * @param durationMs how long text takes to appear that comes slowly. Its alpha is 1 - (1 - t)² at the part t of it,
 * fast at first
 * @param window not used anymore: text that appears piece after piece, like typing or a reveal, is never shown at once
 * before it appeared, however long it is
 * @param maxRanges how many ranges can be appearing at once. With more, two pieces one behind the other whose alphas
 * are the closest become one, so nothing jumps to visible. The pieces one behind the other in a row are drawn in one
 * small layer, with a soft gradient of their alphas, so there is no edge between them
 * @param slide how far below its place the text starts, 0 only fades
 * @param cursorGlide how the cursors of other users move to their new place, see [KodeEditorRemoteCursor], null
 * makes them jump
 * @param maxDurationMs how long text takes to appear that comes fast, [durationMs] if it is shorter
 * @param slowCharsPerSecond up to this rate of a writer, their text appears in [durationMs]
 * @param fastCharsPerSecond from this rate of a writer on, their text appears in [maxDurationMs]
 * @param local how big changes of the user move and appear, null for not at all. Their typing never animates
 */
@Immutable
data class KodeEditorAppearance(
    val durationMs: Int = 320,
    val window: Int = 16,
    val maxRanges: Int = 256,
    val slide: Dp = 0.dp,
    val cursorGlide: KodeEditorCursorGlide? = KodeEditorCursorGlide(),
    val maxDurationMs: Int = 1000,
    val slowCharsPerSecond: Float = 10f,
    val fastCharsPerSecond: Float = 120f,
    val local: KodeEditorLocalAnimations? = KodeEditorLocalAnimations(),
) {
    /** @return how long a piece takes to appear, for a writer who writes [charsPerSecond] */
    internal fun durationFor(charsPerSecond: Float): Int {
        val longest = maxOf(maxDurationMs, durationMs)
        val span = fastCharsPerSecond - slowCharsPerSecond
        if (span <= 0f) return if (charsPerSecond >= fastCharsPerSecond) longest else durationMs
        val part = ((charsPerSecond - slowCharsPerSecond) / span).coerceIn(0f, 1f)
        return (durationMs + (longest - durationMs) * part).roundToInt()
    }
}

/**
 * How the changes of the user animate. Typing, the keyboard and dragging a selection never do: the cursor and the text
 * at it are where they belong at once.
 *
 * @param bigChanges whether paste, undo, redo and replacing with the search move the text behind the change to its
 * new place like the changes of others, and fade the inserted or restored text in, so it is easy to see what changed.
 * The cursor and the selection are at their new place at once
 * @param highlightMs how long the inserted or restored text of such a change takes to appear
 */
@Immutable
data class KodeEditorLocalAnimations(
    val bigChanges: Boolean = true,
    val highlightMs: Int = 400,
)

/** how long the rate of a writer is averaged over, see [KodeEditorAppearance] */
internal const val APPEAR_RATE_MS = 1000f
