package de.markusressel.kodeeditor.library.compose

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
}

/**
 * A part of the text that can be folded
 *
 * @param startLine the line that stays visible and gets the arrow to fold the region
 * @param endLine the last line that gets hidden
 */
data class FoldRegion(val startLine: Int, val endLine: Int)

/**
 * Folds the headings of Markdown: a heading hides everything until the next heading of the same or a higher level.
 * Blank lines at the end of a section stay visible, so folded sections stay apart. Lines in fenced code are no
 * headings. Only headings that start with `#` are found, not those underlined with `===` or `---`.
 */
object MarkdownFoldProvider : KodeEditorFoldProvider {
    private const val MAX_HEADING_LEVEL = 6
    private const val MAX_INDENT = 3
    private const val MIN_FENCE_LENGTH = 3

    override fun foldRegions(text: String): List<FoldRegion> {
        val regions = ArrayList<FoldRegion>()
        // the headings that are still open, by level
        val open = IntArray(MAX_HEADING_LEVEL + 1) { -1 }
        var fenceCharacter = NO_FENCE
        var fenceLength = 0
        var lastWithText = -1
        var line = 0
        var start = 0

        fun close(fromLevel: Int) {
            for (level in fromLevel..MAX_HEADING_LEVEL) {
                val heading = open[level]
                if (heading >= 0 && lastWithText > heading) regions += FoldRegion(heading, lastWithText)
                open[level] = -1
            }
        }

        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            val first = firstNonSpace(text, start, end)
            val indent = first - start
            if (first < end && indent <= MAX_INDENT) {
                val character = text[first]
                if (character == '`' || character == '~') {
                    val length = countRepeated(text, first, end)
                    if (fenceCharacter == NO_FENCE) {
                        if (length >= MIN_FENCE_LENGTH) {
                            fenceCharacter = character
                            fenceLength = length
                        }
                    } else if (
                        character == fenceCharacter && length >= fenceLength &&
                        firstNonSpace(text, first + length, end) == end
                    ) {
                        fenceCharacter = NO_FENCE
                    }
                } else if (character == '#' && fenceCharacter == NO_FENCE) {
                    val level = countRepeated(text, first, end)
                    val after = first + level
                    if (level <= MAX_HEADING_LEVEL && (after == end || text[after] == ' ' || text[after] == '\t')) {
                        close(level)
                        open[level] = line
                    }
                }
            }
            if (first < end) lastWithText = line
            start = end + 1
            line++
        }
        close(1)
        return regions
    }

    private fun firstNonSpace(text: String, start: Int, end: Int): Int {
        var index = start
        while (index < end && (text[index] == ' ' || text[index] == '\t')) index++
        return index
    }

    private fun countRepeated(text: String, start: Int, end: Int): Int {
        var index = start
        while (index < end && text[index] == text[start]) index++
        return index - start
    }

    private const val NO_FENCE = ' '
}
