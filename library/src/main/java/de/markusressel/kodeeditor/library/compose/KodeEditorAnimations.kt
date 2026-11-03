package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Immutable

/**
 * What the [KodeEditor] animates.
 *
 * @param gutterWidth whether the bar next to the text animates to a new width
 * @param lineNumbers whether the numbers of new and removed lines fade
 * @param reflow whether the text, the bar, the selection, the cursor and the overlay move to their new place when lines
 * wrap differently, which only applies with line wrapping
 */
@Immutable
data class KodeEditorAnimations(
    val gutterWidth: Boolean = true,
    val lineNumbers: Boolean = true,
    val reflow: Boolean = true,
) {
    companion object {
        /** Everything jumps to its new place at once */
        val None = KodeEditorAnimations(gutterWidth = false, lineNumbers = false, reflow = false)
    }
}
