package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString

/**
 * Something the editor shows on whole lines, e.g. a highlight of the current line, lines with errors or changed lines,
 * or a note behind the text of a line.
 *
 * Decorations are only asked about lines that are visible, so they stay cheap with a long text. Reading snapshot state
 * in these functions shows the decoration again when that state changes. They work with line numbers, so what belongs
 * to a line has to move when an edit moves that line, see [KodeEditorGutterColumn].
 */
@Stable
interface KodeEditorLineDecoration {
    /**
     * Draws behind the selection and the text of a line, with the top left corner of the visible part at the origin.
     * Use a color that is not opaque, so decorations below this one stay visible.
     *
     * @param line the line and the size of the area
     */
    fun DrawScope.drawBackground(line: DecoratedLine) {}

    /**
     * Text that is shown behind the end of a line. It is not a part of the text of the editor, so the cursor can not
     * get into it. It is cut off at the end of the row in an editor with line wrapping.
     *
     * @param line a line
     * @return the text with its styles, null to show nothing. Remember the result if it does not change
     */
    fun trailingText(line: Int): AnnotatedString? = null

    /**
     * @param line the line whose [trailingText] was tapped
     * @return true if this did something with the tap, otherwise it places the cursor like any tap on the text
     */
    fun onTrailingTextTap(line: Int): Boolean = false
}

/**
 * The area a decoration can draw into for one line, which covers all of its rows.
 *
 * @param line the line
 * @param width the width of the area, which reaches beyond the edges of the editor
 * @param height the height of the area
 * @param textStart where the text of the line starts in the area
 */
class DecoratedLine(
    val line: Int,
    val width: Float,
    val height: Float,
    val textStart: Float,
)
