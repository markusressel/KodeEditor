package de.markusressel.kodeeditor.library.compose.layout


/**
 * A part of a row drawn with an alpha of its own.
 *
 * @param left where it starts in the row
 * @param right where it ends in the row
 * @param alpha how visible it is, without [stops]
 * @param stops how visible it is along the row, from left to right: where in the row and the alpha there
 */
internal class FadingRun(val left: Float, val right: Float, val alpha: Float, val stops: List<Pair<Float, Float>>?)

/**
 * Joins the parts of [parts] that are one behind the other in the row. Each has its alpha in its middle, and the alpha
 * goes from one to the next in a straight line, so neighbours that appear a bit apart have no edge between them. Parts
 * that are not drawn (alpha 0) are not joined.
 */
internal fun gradientRuns(parts: List<FadingPart>): List<FadingRun> {
    val sorted = parts.filter { it.alpha > 0f && it.right > it.left }.sortedBy { it.left }
    val runs = ArrayList<FadingRun>()
    var index = 0
    while (index < sorted.size) {
        var last = index
        while (last + 1 < sorted.size && sorted[last + 1].left <= sorted[last].right + GRADIENT_GAP) last++
        val first = sorted[index]
        if (last == index) {
            runs += FadingRun(first.left, first.right, first.alpha, null)
        } else {
            val stops = ArrayList<Pair<Float, Float>>(last - index + 3)
            stops += first.left to first.alpha
            for (part in index..last) {
                val piece = sorted[part]
                stops += (piece.left + piece.right) / 2 to piece.alpha
            }
            stops += sorted[last].right to sorted[last].alpha
            runs += FadingRun(first.left, sorted[last].right, first.alpha, stops)
        }
        index = last + 1
    }
    return runs
}

/** how far apart two parts can be and still be one behind the other, in px */
private const val GRADIENT_GAP = 0.5f
