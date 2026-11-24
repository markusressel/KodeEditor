package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import de.markusressel.kodeeditor.library.compose.input.TextLines

/**
 * What the overlay sees: the places of things as they are on the screen, also while they move during a reflow. Reading
 * them updates the overlay on each of its frames.
 */
internal class ShownEditorLayout(private val layout: EditorLineLayout) : KodeEditorLayout by layout {
    override fun getLineTop(line: Int) = layout.getLineTop(line) + layout.shownLineDistance(line)
    override fun getLineBottom(line: Int) = layout.getLineBottom(line) + layout.shownLineDistance(line)
    override fun getLineBaseline(line: Int) = layout.getLineBaseline(line) + layout.shownLineDistance(line)
    override fun getCursorRect(offset: Int) = layout.shownCursorRect(offset)
    override fun getPathForRange(start: Int, end: Int) = layout.shownPathForRange(start, end)
}

/** moving the cursor with the keys uses the same rows the editor shows */
internal class EditorTextLines(private val layout: EditorLineLayout) : TextLines {
    override val lineCount get() = layout.rowCount
    override fun lineOf(offset: Int) = layout.rowOf(offset)
    override fun lineAt(y: Float) = layout.rowAt(y)
    override fun topOf(line: Int) = layout.rowTop(line)
    override fun xOf(offset: Int) = layout.getCursorRect(offset).left
    override fun offsetAt(line: Int, x: Float) =
        layout.getOffsetForPosition(Offset(x, layout.rowTop(line) + layout.lineHeight / 2))
    override fun startOf(offset: Int) = layout.rowStart(offset)
    override fun endOf(offset: Int) = layout.rowEnd(offset)
    override fun shownPosition(offset: Int, forward: Boolean) = layout.shownPosition(offset, forward)
}
