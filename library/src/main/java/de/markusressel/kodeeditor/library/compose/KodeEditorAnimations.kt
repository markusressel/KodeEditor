package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Immutable

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
 * @param scrolling whether the view can move to where [KodeEditorViewState] scrolls to, instead of jumping there
 */
@Immutable
data class KodeEditorAnimations(
    val gutterWidth: Boolean = true,
    val lineNumbers: Boolean = true,
    val reflow: Boolean = true,
    val folding: Boolean = true,
    val scrolling: Boolean = true,
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
