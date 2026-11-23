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
 * place while it does. This only changes how the text is drawn, nothing is measured again. The system setting that
 * scales the duration of animations scales this too, and turns it off with a scale of 0.
 *
 * @param durationMs how long the text takes to appear
 * @param window of text that appears piece after piece, like typing, how many of the last characters can still be
 * appearing. Older pieces are shown at once
 * @param maxRanges how many ranges can be appearing at once, the oldest is shown at once when there would be more.
 * Each costs a small layer on each row it is in
 * @param slide how far below its place the text starts, 0 only fades
 * @param cursorGlide how the cursors of other users move to their new place, see [KodeEditorRemoteCursor], null
 * makes them jump
 */
@Immutable
data class KodeEditorAppearance(
    val durationMs: Int = 250,
    val window: Int = 16,
    val maxRanges: Int = 24,
    val slide: Dp = 0.dp,
    val cursorGlide: KodeEditorCursorGlide? = KodeEditorCursorGlide(),
)
