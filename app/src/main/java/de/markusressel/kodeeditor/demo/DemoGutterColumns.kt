package de.markusressel.kodeeditor.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.markusressel.kodeeditor.library.compose.GutterCell
import de.markusressel.kodeeditor.library.compose.DecoratedLine
import de.markusressel.kodeeditor.library.compose.KodeEditorGutterColumn
import de.markusressel.kodeeditor.library.compose.KodeEditorLineDecoration
import kotlin.math.min

private val HEADING_WITHOUT_SPACE = Regex("^#{1,6}[^#\\s].*")

/**
 * @param text the text of the editor
 * @return the warning of each line that has one, by line
 */
fun findWarnings(text: String): Map<Int, String> = buildMap {
    text.lineSequence().forEachIndexed { line, content ->
        when {
            content.contains("TODO") -> put(line, "This line has a TODO")
            content.isNotEmpty() && content.last().isWhitespace() -> put(line, "Trailing whitespace")
            HEADING_WITHOUT_SPACE.matches(content) -> put(line, "A heading needs a space after the #")
        }
    }
}

/**
 * Shows warnings that depend on the text. A tap on a marker selects its warning, a tap on any other line places the
 * cursor as usual.
 *
 * @param width the width of the column in pixels
 * @param color the color of the markers
 */
class WarningColumn(
    private val width: Int,
    private val color: Color,
) : KodeEditorGutterColumn, KodeEditorLineDecoration {
    /** Setting an equal map changes nothing, so typing within a line does not draw the bar again. */
    var warnings by mutableStateOf(emptyMap<Int, String>())

    private var selectedLine by mutableStateOf<Int?>(null)

    /** The selected warning, gone as soon as the text does not have it anymore */
    val selectedMessage: String?
        get() = selectedLine?.let { line -> warnings[line]?.let { "Line ${line + 1}: $it" } }

    override fun width(lineCount: Int) = width

    override fun DrawScope.drawCell(cell: GutterCell) {
        if (cell.line !in warnings) return
        val center = Offset(cell.width / 2, cell.height / 2)
        val radius = min(cell.width, cell.height) * 0.35f
        val triangle = Path().apply {
            moveTo(center.x, center.y - radius)
            lineTo(center.x + radius, center.y + radius)
            lineTo(center.x - radius, center.y + radius)
            close()
        }
        drawPath(triangle, color)
    }

    override fun DrawScope.drawBackground(line: DecoratedLine) {
        if (line.line in warnings) drawRect(color.copy(alpha = 0.12f), size = Size(line.width, line.height))
    }

    override fun onTap(line: Int): Boolean {
        if (line !in warnings) return false
        selectedLine = line
        return true
    }

    /** The selected warning follows its line. */
    fun onEdit(edit: LineEdit) {
        selectedLine = selectedLine?.let(edit::lineAfterEdit)
    }

    fun clearSelection() {
        selectedLine = null
    }
}

/**
 * Keeps its own state: a tap on the column sets a bookmark on that line, or removes it. Bookmarks follow the text of
 * their line when it moves, and go away with it.
 *
 * @param width the width of the column in pixels
 * @param color the color of the markers
 */
class BookmarkColumn(private val width: Int, private val color: Color) : KodeEditorGutterColumn {
    private val lines = mutableStateListOf<Int>()

    override fun width(lineCount: Int) = width

    override fun DrawScope.drawCell(cell: GutterCell) {
        if (cell.line in lines) {
            val radius = min(cell.width, cell.height) * 0.3f
            drawCircle(color, radius = radius, center = Offset(cell.width / 2, cell.height / 2))
        }
    }

    override fun onTap(line: Int): Boolean {
        if (!lines.remove(line)) lines += line
        return true
    }

    fun onEdit(edit: LineEdit) {
        val moved = lines.mapNotNull(edit::lineAfterEdit).distinct()
        if (moved != lines.toList()) {
            lines.clear()
            lines += moved
        }
    }

    fun clear() {
        lines.clear()
    }
}
