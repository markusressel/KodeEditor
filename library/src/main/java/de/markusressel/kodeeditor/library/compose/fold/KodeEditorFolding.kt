package de.markusressel.kodeeditor.library.compose.fold

/**
 * Finds what can be folded in a text, which depends on its language. The editor does everything else.
 *
 * The result of unchanged lines should stay the same, this runs on the main thread after each change of the text, so
 * it should only take a look at each line once.
 */
fun interface KodeEditorFoldProvider {
    /**
     * @param text the whole text
     * @return what can be folded, in any order. Regions can be nested
     */
    fun foldRegions(text: String): List<FoldRegion>

    /**
     * Runs on another thread, unlike [foldRegions].
     *
     * @param text the whole text
     * @return parts of lines that are shown as something shorter. In any order, and of those that overlap, the first and
     * longest one is used
     */
    fun inlineFolds(text: String): List<InlineFold> = emptyList()
}

/**
 * A part of the text that can be folded
 *
 * @param startLine the line that stays visible and gets the arrow to fold the region
 * @param endLine the last line that gets hidden
 */
data class FoldRegion(val startLine: Int, val endLine: Int)

/**
 * A part of a line that is shown as a [placeholder]. The text itself stays what it is. Tapping the placeholder shows the
 * text, and it goes back when the cursor leaves. The arrow keys move over it.
 *
 * @param start where the part starts in the text
 * @param end where the part ends in the text, which has to be in the same line
 * @param placeholder what is shown instead, a single line
 */
data class InlineFold(val start: Int, val end: Int, val placeholder: String)
