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

    /**
     * @param text the whole text
     * @return parts of lines that are shown as something shorter, unless the cursor is at them. In any order, and of
     * those that overlap, the first and longest one is used
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
 * A part of a line that is shown as a [placeholder]. The text itself stays what it is. Tapping the placeholder or moving
 * the cursor to it shows the text, and it goes back when the cursor leaves.
 *
 * @param start where the part starts in the text
 * @param end where the part ends in the text, which has to be in the same line
 * @param placeholder what is shown instead, a single line
 */
data class InlineFold(val start: Int, val end: Int, val placeholder: String)

/**
 * Folds the headings of Markdown: a heading hides everything until the next heading of the same or a higher level.
 * Blank lines at the end of a section stay visible, so folded sections stay apart. Lines in fenced code are no
 * headings. Only headings that start with `#` are found, not those underlined with `===` or `---`.
 *
 * Within lines it shortens the targets of links and images, long addresses, comments and character references. Code is
 * left alone.
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

    override fun inlineFolds(text: String): List<InlineFold> {
        val folds = ArrayList<InlineFold>()
        var fenceCharacter = NO_FENCE
        var fenceLength = 0
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            val first = firstNonSpace(text, start, end)
            val character = if (first < end && first - start <= MAX_INDENT) text[first] else NO_FENCE
            val length = if (character == '`' || character == '~') countRepeated(text, first, end) else 0
            when {
                fenceCharacter == NO_FENCE && length >= MIN_FENCE_LENGTH -> {
                    fenceCharacter = character
                    fenceLength = length
                }

                fenceCharacter == NO_FENCE -> inlineFoldsOfLine(text, start, end, folds)
                character == fenceCharacter && length >= fenceLength && firstNonSpace(text, first + length, end) == end ->
                    fenceCharacter = NO_FENCE
            }
            start = end + 1
        }
        // the address in a link is its target, which comes first
        return folds.distinctBy { it.start }
    }

    private fun inlineFoldsOfLine(text: String, start: Int, end: Int, folds: MutableList<InlineFold>) {
        var index = start
        while (index < end) {
            when (text[index]) {
                '\\' -> index++
                '`' -> index = endOfCodeSpan(text, index, end)
                '[' -> linkTarget(text, index, end)?.let { (targetStart, targetEnd) ->
                    if (targetEnd - targetStart >= MIN_TARGET_LENGTH) folds += InlineFold(targetStart, targetEnd, ELLIPSIS)
                }

                '<' -> if (text.startsWith(COMMENT_START, index)) {
                    val close = text.indexOf(COMMENT_END, index + COMMENT_START.length)
                    if (close in 0 until end) {
                        val contentStart = index + COMMENT_START.length
                        if (close - contentStart >= MIN_TARGET_LENGTH) folds += InlineFold(contentStart, close, ELLIPSIS)
                        index = close + COMMENT_END.length - 1
                    }
                }

                '&' -> characterReference(text, index, end)?.let { folds += it }
                'h' -> addressAt(text, index, start, end)?.let { fold ->
                    folds += fold
                    index = fold.end - 1
                }
            }
            index++
        }
    }

    /** @return the position after the code that starts at [start], the next position if the code does not end */
    private fun endOfCodeSpan(text: String, start: Int, end: Int): Int {
        val length = countRepeated(text, start, end)
        var index = start + length
        while (index < end) {
            if (text[index] == '`') {
                val run = countRepeated(text, index, end)
                if (run == length) return index + run - 1
                index += run
            } else {
                index++
            }
        }
        return start + length - 1
    }

    /** @return the target of the link whose text starts at [start], null if this is no link */
    private fun linkTarget(text: String, start: Int, end: Int): Pair<Int, Int>? {
        var depth = 0
        var index = start
        while (index < end) {
            when (text[index]) {
                '\\' -> index++
                '[' -> depth++
                ']' -> if (--depth == 0) break
            }
            index++
        }
        if (index + 1 >= end || text[index] != ']' || text[index + 1] != '(') return null
        val targetStart = index + 2
        depth = 1
        index = targetStart
        while (index < end) {
            when (text[index]) {
                '\\' -> index++
                '(' -> depth++
                ')' -> if (--depth == 0) return targetStart to index
            }
            index++
        }
        return null
    }

    private fun characterReference(text: String, start: Int, end: Int): InlineFold? {
        val close = text.indexOf(';', start)
        if (close !in 0 until end || close - start > MAX_REFERENCE_LENGTH) return null
        val character = CHARACTER_REFERENCES[text.substring(start + 1, close)] ?: return null
        return InlineFold(start, close + 1, character)
    }

    /** @return the placeholder of the address that starts at [start], null if there is none or it is short */
    private fun addressAt(text: String, start: Int, lineStart: Int, end: Int): InlineFold? {
        if (start > lineStart && text[start - 1].isLetterOrDigit()) return null
        val hostStart = when {
            text.startsWith("https://", start) -> start + "https://".length
            text.startsWith("http://", start) -> start + "http://".length
            else -> return null
        }
        var stop = hostStart
        while (stop < end && !text[stop].isWhitespace() && text[stop] != '<' && text[stop] != '>') stop++
        while (stop > hostStart && text[stop - 1] in ADDRESS_END_PUNCTUATION) stop--
        var hostEnd = hostStart
        while (hostEnd < stop && text[hostEnd] != '/' && text[hostEnd] != '?' && text[hostEnd] != '#') hostEnd++
        if (hostEnd == hostStart) return null
        val placeholder = text.substring(hostStart, hostEnd) + if (hostEnd < stop) "/$ELLIPSIS" else ""
        return InlineFold(start, stop, placeholder).takeIf { stop - start - placeholder.length >= MIN_SAVED_CHARACTERS }
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
    private const val ELLIPSIS = "\u2026"
    private const val MIN_TARGET_LENGTH = 12
    private const val MIN_SAVED_CHARACTERS = 12
    private const val COMMENT_START = "<!--"
    private const val COMMENT_END = "-->"
    private const val ADDRESS_END_PUNCTUATION = ".,;:!?)'\"*_~]"
    private const val MAX_REFERENCE_LENGTH = 8

    private val CHARACTER_REFERENCES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "nbsp" to "\u00B7", "copy" to "\u00A9",
        "reg" to "\u00AE", "trade" to "\u2122", "mdash" to "\u2014", "ndash" to "\u2013", "hellip" to "\u2026",
        "rarr" to "\u2192", "larr" to "\u2190", "uarr" to "\u2191", "darr" to "\u2193", "check" to "\u2713",
    )
}
