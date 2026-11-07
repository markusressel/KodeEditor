package de.markusressel.kodeeditor.library.compose

/**
 * Folds Java properties: a value that goes on over several lines with a backslash at the end, two or more comment
 * lines in a row, and a group that starts with a comment and has at least two entries. A group ends with a blank line
 * or the next comment after an entry.
 */
object PropertiesFoldProvider : KodeEditorFoldProvider {
    private const val MIN_GROUP_ENTRIES = 2

    override fun foldRegions(text: String): List<FoldRegion> {
        val regions = ArrayList<FoldRegion>()
        var continued = -1
        var commentStart = -1
        var commentEnd = -1
        var group = -1
        var groupEntries = 0
        var groupEnd = -1
        var line = 0
        var start = 0

        fun add(startLine: Int, endLine: Int) {
            if (startLine >= 0 && endLine > startLine) regions += FoldRegion(startLine, endLine)
        }

        fun closeComment() {
            add(commentStart, commentEnd)
            commentStart = -1
        }

        fun closeGroup() {
            if (groupEntries >= MIN_GROUP_ENTRIES) add(group, groupEnd)
            group = -1
            groupEntries = 0
        }

        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val contentEnd = if (end > start && text[end - 1] == '\r') end - 1 else end
            var first = start
            while (first < contentEnd && text[first].let { it == ' ' || it == '\t' || it == '\u000C' }) first++
            when {
                continued >= 0 -> {
                    groupEnd = line
                    if (!endsWithBackslash(text, start, contentEnd)) {
                        add(continued, line)
                        continued = -1
                    }
                }

                first == contentEnd -> {
                    closeComment()
                    closeGroup()
                }

                text[first] == '#' || text[first] == '!' -> {
                    if (commentStart < 0) {
                        closeGroup()
                        commentStart = line
                        group = line
                    }
                    commentEnd = line
                }

                else -> {
                    closeComment()
                    if (group >= 0) {
                        groupEntries++
                        groupEnd = line
                    }
                    if (endsWithBackslash(text, first, contentEnd)) continued = line
                }
            }
            start = end + 1
            line++
        }
        add(continued, line - 1)
        closeComment()
        closeGroup()
        return outermost(regions)
    }

    /** @return whether the line ends with an odd count of backslashes, so it goes on in the next line */
    private fun endsWithBackslash(text: String, start: Int, end: Int): Boolean {
        var index = end
        while (index > start && text[index - 1] == '\\') index--
        return (end - index) % 2 == 1
    }

    /** @return one region per line, the longest one */
    private fun outermost(regions: List<FoldRegion>): List<FoldRegion> {
        if (regions.size < 2) return regions
        val result = ArrayList<FoldRegion>(regions.size)
        for (region in regions.sortedWith(compareBy<FoldRegion> { it.startLine }.thenByDescending { it.endLine })) {
            if (result.isEmpty() || result.last().startLine != region.startLine) result += region
        }
        return result
    }
}
