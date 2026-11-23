package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * [KodeEditorState.applyRemote], null shows it at once. Nothing else ever appears animated
 */
@Immutable
data class KodeEditorAnimations(
    val gutterWidth: Boolean = true,
    val lineNumbers: Boolean = true,
    val reflow: Boolean = true,
    val folding: Boolean = true,
    val scrolling: Boolean = true,
    val appearance: KodeEditorAppearance? = null,
) {
    companion object {
        /** Everything jumps to its new place at once */
        val None = KodeEditorAnimations(
            gutterWidth = false,
            lineNumbers = false,
            reflow = false,
            folding = false,
            scrolling = false,
        )
    }
}

/**
 * How text that the application marks appears, e.g. text that somebody else writes. It fades in, and can rise into its
 * place while it does. This only changes how the text is drawn, nothing is measured again. Text that somebody else
 * inserts with [KodeEditorState.applyRemote] takes its room at once, also while it is still hidden, and the text behind
 * it slides out of the way, into other rows too, like the cursors glide (see [KodeEditorCursorGlide.durationMs]). The
 * system setting that scales the duration of animations scales this too, and turns it off with a scale of 0.
 *
 * @param durationMs how long the text takes to appear. Its alpha is 1 - (1 - t)² at the part t of it, fast at first
 * @param window not used anymore: text that appears piece after piece, like typing or a reveal, is never shown at once
 * before it appeared, however long it is
 * @param maxRanges how many ranges can be appearing at once. With more, two pieces one behind the other whose alphas
 * are the closest become one, so nothing jumps to visible. The pieces one behind the other in a row are drawn in one
 * small layer, with a soft gradient of their alphas, so there is no edge between them
 * @param slide how far below its place the text starts, 0 only fades
 * @param cursorGlide how the cursors of other users move to their new place, see [KodeEditorRemoteCursor], null
 * makes them jump
 */
@Immutable
data class KodeEditorAppearance(
    val durationMs: Int = 250,
    val window: Int = 16,
    val maxRanges: Int = 256,
    val slide: Dp = 0.dp,
    val cursorGlide: KodeEditorCursorGlide? = KodeEditorCursorGlide(),
)
