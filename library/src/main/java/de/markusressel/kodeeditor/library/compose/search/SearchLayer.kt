package de.markusressel.kodeeditor.library.compose.search

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.layout.placedAt
import de.markusressel.kodeeditor.library.compose.search.KodeEditorSearch

/**
 * Draws the matches of a [KodeEditorSearch] in a layer of its own, below the selection and the text. A match in folded
 * lines marks the start of the row of its fold instead.
 *
 * @param layout where the text is
 * @param search the matches to draw
 * @param window the part of the document that is drawn
 * @param matchColor the color behind each match
 * @param currentColor the color behind the current match
 */
@Composable
internal fun SearchLayer(
    layout: EditorLineLayout,
    search: KodeEditorSearch,
    window: State<IntRect>,
    matchColor: Color,
    currentColor: Color,
) {
    Box(
        modifier = Modifier
            .placedAt { window.value }
            .graphicsLayer {}
            .drawBehind {
                val matches = search.matches
                if (matches.isEmpty()) return@drawBehind
                val current = search.current
                val area = window.value
                val first = layout.getLineForVerticalPosition(area.top.toFloat())
                val last = layout.getLineForVerticalPosition(area.bottom.toFloat())
                val start = layout.getLineStart(first)
                val end = layout.getLineEnd(last)
                val markWidth = FOLDED_MATCH_MARK_WIDTH.toPx()
                var lastMarkTop = Float.NaN
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    // the first match that ends behind the start of the drawn lines
                    var index = matches.binarySearchBy(start) { it.max }.let { if (it < 0) -it - 1 else it }
                    while (index < matches.size && matches[index].min <= end) {
                        val match = matches[index++]
                        val color = if (match == current) currentColor else matchColor
                        val line = layout.getLineForOffset(match.min)
                        if (layout.isLineHidden(line)) {
                            val top = layout.getLineTop(line) + layout.shownLineDistance(line)
                            if (top != lastMarkTop || match == current) {
                                drawRect(color, Offset(0f, top), Size(markWidth, layout.lineHeight))
                                lastMarkTop = top
                            }
                        } else {
                            drawPath(layout.shownPathForRange(match.min, match.max), color)
                        }
                    }
                }
            },
    )
}

/** how wide the mark of a match in folded lines is */
private val FOLDED_MATCH_MARK_WIDTH = 3.dp
