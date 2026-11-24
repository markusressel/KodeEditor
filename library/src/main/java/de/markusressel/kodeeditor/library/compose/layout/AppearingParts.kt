package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState

/**
 * The parts of the drawn rows that appear with an alpha of their own (R4 of docs/reveal.md), and where appearing text
 * is not drawn yet because the caret of its writer has not passed it (R7).
 *
 * @param state the text, with its appearing ranges
 * @param lineHeight the height of a row
 * @param rowStart where a row starts in the text
 * @param rowEnd where a row ends in the text
 * @param inRow where a position in the text is in the layout of its row
 */
internal class AppearingParts(
    private val state: KodeEditorState,
    private val lineHeight: Float,
    private val rowStart: (row: Int) -> Int,
    private val rowEnd: (row: Int) -> Int,
    private val inRow: (row: Int, offset: Int) -> Int,
) {
    /**
     * @param line a row that is drawn
     * @param cuts where appearing text is not drawn yet, see [caretCuts], once for all rows of a frame
     * @return the parts of the row that are drawn with an alpha of their own, null if there are none
     */
    fun fadingParts(line: EditorLineLayout.DrawnLine, cuts: List<CaretCut> = caretCuts()): List<FadingPart>? {
        val alphas = state.rangeAlphas
        if (alphas.isEmpty()) return null
        val hidden = behindCarets(line, cuts)
        val start = rowStart(line.row)
        val end = rowEnd(line.row)
        var result: MutableList<FadingPart>? = null
        for (range in alphas) {
            if (range.item >= 1f || range.end <= start || range.start >= end) continue
            val from = inRow(line.row, range.start.coerceAtLeast(start))
            val to = inRow(line.row, range.end.coerceAtMost(end))
            if (from >= to) continue
            val left = line.layout.getHorizontalPosition(from, usePrimaryDirection = true)
            val right = line.layout.getHorizontalPosition(to, usePrimaryDirection = true)
            val parts = result ?: mutableListOf<FadingPart>().also { result = it }
            parts += FadingPart(minOf(left, right), maxOf(left, right), range.item.coerceAtLeast(0f))
        }
        if (hidden.isEmpty()) return result
        // what is not drawn yet is not drawn faded either
        val faded = result.orEmpty().flatMap { part ->
            hidden.fold(listOf(part)) { pieces, cut ->
                pieces.flatMap { piece ->
                    if (cut.right <= piece.left || cut.left >= piece.right) {
                        listOf(piece)
                    } else {
                        listOfNotNull(
                            FadingPart(piece.left, cut.left, piece.alpha).takeIf { cut.left > piece.left },
                            FadingPart(cut.right, piece.right, piece.alpha).takeIf { cut.right < piece.right },
                        )
                    }
                }
            }
        }
        return faded + hidden
    }

    /** the cursors of other users, where each is in the text and where its caret is drawn, see [behindCarets] */
    var remoteCarets: () -> List<Pair<Int, Offset>> = { emptyList() }

    /** how far the text at a position is drawn away from its row, see [WrapCarries], the cut is in its row */
    var carriedBy: (Int) -> Offset = { Offset.Zero }

    /**
     * The appearing text right at the cursor of somebody else, which may be several pieces one behind the other, and
     * pieces behind it, whose cursor update comes a frame later.
     *
     * @param start where that text starts
     * @param end where it ends
     * @param caret the top left of where their caret is drawn
     * @param hiddenBehind whether hidden text follows it, so nothing shown is cut off at its end
     */
    class CaretCut(val start: Int, val end: Int, val caret: Offset, val hiddenBehind: Boolean)

    /**
     * Text that appears in front of the cursor of somebody else, which they just typed or which is revealed for them,
     * shows only where their caret has passed already, see [fadingParts]. Read once for all rows of a frame.
     *
     * @return the appearing text at each caret of the others
     */
    fun caretCuts(): List<CaretCut> {
        val alphas = state.rangeAlphas
        if (alphas.isEmpty()) return emptyList()
        val carets = remoteCarets()
        if (carets.isEmpty()) return emptyList()
        // the first range in the list that ends or starts at a position, like a search from its start
        val endingAt = HashMap<Int, AnnotatedString.Range<Float>>()
        val startingAt = HashMap<Int, AnnotatedString.Range<Float>>()
        alphas.forEach {
            if (it.start < it.end) {
                endingAt.putIfAbsent(it.end, it)
                startingAt.putIfAbsent(it.start, it)
            }
        }
        val rangeStyles = state.rangeStyles
        return carets.mapNotNull { (offset, drawn) ->
            var start = offset
            while (true) start = endingAt[start]?.start ?: break
            var end = offset
            while (true) end = startingAt[end]?.end ?: break
            if (start == end) return@mapNotNull null
            val hiddenBehind = rangeStyles.any { it.item.color == Color.Transparent && it.start <= end && end < it.end }
            CaretCut(start, end, drawn - carriedBy(offset), hiddenBehind)
        }
    }

    /**
     * Text that appears in front of the cursor of somebody else is not drawn behind the caret, so it never shows ahead
     * of it while the caret glides.
     *
     * @return the parts of the [line] that are not drawn yet, as parts without alpha
     */
    private fun behindCarets(line: EditorLineLayout.DrawnLine, cuts: List<CaretCut>): List<FadingPart> {
        if (cuts.isEmpty()) return emptyList()
        val firstInRow = rowStart(line.row)
        val lastInRow = rowEnd(line.row)
        var parts: MutableList<FadingPart>? = null
        cuts.forEach { cut ->
            val from = cut.start.coerceAtLeast(firstInRow)
            val to = cut.end.coerceAtMost(lastInRow)
            if (from >= to) return@forEach
            val caretRow = ((cut.caret.y + lineHeight / 2) / lineHeight).toInt()
            if (line.row < caretRow) return@forEach
            val left = line.layout.getHorizontalPosition(inRow(line.row, from), usePrimaryDirection = true)
            var right = line.layout.getHorizontalPosition(inRow(line.row, to), usePrimaryDirection = true)
            // a letter can reach a bit beyond its place, where nothing is shown behind it that would be cut off
            val shownBehind = cut.end < lastInRow && !cut.hiddenBehind
            if (!shownBehind) right += lineHeight / 2
            val hiddenFrom = if (line.row == caretRow) maxOf(left, cut.caret.x - line.position.x) else left
            if (hiddenFrom >= right) return@forEach
            val list = parts ?: mutableListOf<FadingPart>().also { parts = it }
            list += FadingPart(hiddenFrom, right, 0f)
        }
        return parts ?: emptyList()
    }
}

/**
 * @param left where the part starts in the row
 * @param right where the part ends in the row
 * @param alpha how visible the part is
 */
internal class FadingPart(val left: Float, val right: Float, val alpha: Float)
