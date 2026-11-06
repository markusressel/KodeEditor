package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the scrollbars of the [KodeEditor] look and when they show. Their color is
 * [KodeEditorColors.scrollbarColor].
 *
 * @param thickness the thickness of a thumb
 * @param margin the space between a thumb and the edge of the editor
 * @param touchSize the size of the area across a thumb that can be touched to drag it, the thumb itself is too thin
 * @param minThumbLength the shortest a thumb gets in a long text, so it can still be hit
 * @param hideDelayMillis how long the bars stay after the view stopped and the user let go of it
 * @param fadeInMillis how long the bars take to fade in when the view starts to move, 0 shows them at once
 * @param fadeOutMillis how long the bars take to fade out, 0 hides them at once
 */
@Immutable
data class KodeEditorScrollbarStyle(
    val thickness: Dp = 4.dp,
    val margin: Dp = 2.dp,
    val touchSize: Dp = 24.dp,
    val minThumbLength: Dp = 32.dp,
    val hideDelayMillis: Long = 1000L,
    val fadeInMillis: Int = 150,
    val fadeOutMillis: Int = 250,
) {
    init {
        require(hideDelayMillis >= 0) { "hideDelayMillis must not be negative" }
        require(fadeInMillis >= 0) { "fadeInMillis must not be negative" }
        require(fadeOutMillis >= 0) { "fadeOutMillis must not be negative" }
    }
}
