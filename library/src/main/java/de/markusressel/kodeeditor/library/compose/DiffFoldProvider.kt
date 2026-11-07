package de.markusressel.kodeeditor.library.compose

/**
 * Folds unified diffs: each file from its `diff …` or `Index: …` line, or from a `---` line that a `+++` line follows,
 * until the next file, and each hunk from its `@@ … @@` line until the next hunk or file. Blank lines at the end stay
 * visible.
 *
 * The line counts in a hunk header are used, so a removed line that starts with `--` is no new file, and what follows
 * the last hunk of a patch, like a signature, is not part of it. A hunk with counts that do not fit ends at the next
 * hunk or file.
 */
object DiffFoldProvider : KodeEditorFoldProvider {
    override fun foldRegions(text: String): List<FoldRegion> {
        val regions = ArrayList<FoldRegion>()
        var file = -1
        var fileEnd = -1
        // whether the file started with a `diff` line, which its `---` and `+++` lines follow
        var fileHasCommand = false
        var fileHasHunk = false
        var hunk = -1
        var hunkEnd = -1
        // lines left in the hunk, or -1 if they are not known
        var oldLeft = -1
        var newLeft = -1
        var line = 0
        var start = 0

        fun closeHunk() {
            if (hunk >= 0 && hunkEnd > hunk) regions += FoldRegion(hunk, hunkEnd)
            hunk = -1
            oldLeft = -1
            newLeft = -1
        }

        fun closeFile() {
            closeHunk()
            if (file >= 0 && fileEnd > file) regions += FoldRegion(file, fileEnd)
            file = -1
        }

        fun openFile(hasCommand: Boolean) {
            closeFile()
            file = line
            fileEnd = line
            fileHasCommand = hasCommand
            fileHasHunk = false
        }

        while (start <= text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val contentEnd = if (end > start && text[end - 1] == '\r') end - 1 else end
            val first = if (start < contentEnd) text[start] else ' '
            var counted = false
            if (oldLeft > 0 || newLeft > 0) {
                counted = when (first) {
                    ' ' -> oldLeft > 0 && newLeft > 0
                    '-' -> oldLeft > 0
                    '+' -> newLeft > 0
                    else -> first == '\\'
                }
                if (counted) {
                    if (first != '+' && first != '\\') oldLeft--
                    if (first != '-' && first != '\\') newLeft--
                    hunkEnd = line
                    fileEnd = line
                } else {
                    // the counts do not fit, the hunk goes on until the next one
                    oldLeft = -1
                    newLeft = -1
                }
            }
            if (!counted) {
                val nextStart = end + 1
                when {
                    text.startsWith("diff ", start) || text.startsWith("Index: ", start) -> openFile(true)
                    text.startsWith("--- ", start) && text.startsWith("+++ ", nextStart) -> {
                        if (file < 0 || !fileHasCommand || fileHasHunk) openFile(false)
                        closeHunk()
                        // the +++ line belongs to it too
                        val afterNext = text.indexOf('\n', nextStart)
                        fileEnd = line + 1
                        line++
                        start = if (afterNext < 0) text.length + 1 else afterNext + 1
                        line++
                        continue
                    }

                    text.startsWith("@@", start) -> {
                        closeHunk()
                        hunk = line
                        hunkEnd = line
                        fileHasHunk = true
                        fileEnd = line
                        readCounts(text, start, contentEnd)?.let { (old, new) ->
                            oldLeft = old
                            newLeft = new
                        }
                    }

                    first == '\\' && hunk >= 0 -> {
                        hunkEnd = line
                        fileEnd = line
                    }

                    // the last counted line ended the hunk, what follows does not belong to it
                    hunk >= 0 && oldLeft == 0 && newLeft == 0 -> closeHunk()
                    contentEnd > start && !isBlank(text, start, contentEnd) -> {
                        if (hunk >= 0) hunkEnd = line
                        if (file >= 0 && (hunk >= 0 || !fileHasHunk)) fileEnd = line
                    }
                }
            }
            start = end + 1
            line++
        }
        closeFile()
        return regions
    }

    /** @return the counts of old and new lines of the hunk header from [start], or null if it has none */
    private fun readCounts(text: String, start: Int, end: Int): Pair<Int, Int>? {
        var index = start
        while (index < end && text[index] == '@') index++
        while (index < end && text[index] == ' ') index++
        if (index >= end || text[index] != '-') return null
        val old = readRange(text, index + 1, end) ?: return null
        index = old.second
        while (index < end && text[index] == ' ') index++
        if (index >= end || text[index] != '+') return null
        val new = readRange(text, index + 1, end) ?: return null
        return old.first to new.first
    }

    /** @return the count of a range like `12,3` from [start], 1 if it has none, and where it ends */
    private fun readRange(text: String, start: Int, end: Int): Pair<Int, Int>? {
        var index = start
        while (index < end && text[index].isDigit()) index++
        if (index == start) return null
        if (index >= end || text[index] != ',') return 1 to index
        val countStart = index + 1
        var count = 0
        index = countStart
        while (index < end && text[index].isDigit() && count < MAX_COUNT) {
            count = count * 10 + (text[index] - '0')
            index++
        }
        return if (index == countStart) null else count to index
    }

    private fun isBlank(text: String, start: Int, end: Int): Boolean {
        for (index in start until end) if (text[index] != ' ' && text[index] != '\t') return false
        return true
    }

    private const val MAX_COUNT = 100_000_000
}
