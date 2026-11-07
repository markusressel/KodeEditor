package de.markusressel.kodeeditor.library.compose

/**
 * Folds by indentation, for languages where it shows the structure: a line folds the lines below it that are
 * indented deeper than it. Blank lines and lines with only a comment don't end a block, and blank lines at the end of a
 * block stay visible, so folded blocks stay apart. A comment at the end of a block belongs to it when it is indented
 * deeper than the line that folds.
 *
 * Use [PythonFoldProvider] or [YamlFoldProvider] for those languages, they know more about them.
 *
 * @param tabWidth the columns between tab stops, a tab moves the indentation to the next one
 * @param lineCommentPrefixes what lines with only a comment start with after their indentation
 */
class IndentFoldProvider(
    private val tabWidth: Int = DEFAULT_TAB_WIDTH,
    private val lineCommentPrefixes: List<String> = listOf("#"),
) : KodeEditorFoldProvider {

    init {
        require(tabWidth > 0) { "tabWidth must be positive, was $tabWidth" }
    }

    override fun foldRegions(text: String): List<FoldRegion> = with(IndentLines) {
        val regions = ArrayList<FoldRegion>()
        val blocks = IndentBlocks(regions)
        var line = 0
        var start = 0
        while (start <= text.length) {
            val end = lineEnd(text, start)
            val first = firstNonSpace(text, start, end)
            if (first < end) {
                val indent = indentColumns(text, start, first, tabWidth)
                if (isComment(text, first)) {
                    blocks.comment(line, indent)
                } else {
                    blocks.begin(line, indent)
                    blocks.text(line)
                }
            }
            start = nextLineStart(text, start)
            line++
        }
        blocks.closeAll()
        regions
    }

    private fun isComment(text: String, first: Int): Boolean {
        for (prefix in lineCommentPrefixes) {
            if (prefix.isNotEmpty() && text.startsWith(prefix, first)) return true
        }
        return false
    }

    private companion object {
        const val DEFAULT_TAB_WIDTH = 4
    }
}

/**
 * The blocks of an indented text that are still open, from the outermost to the innermost, each indented deeper than
 * the one before. Turns them into [FoldRegion]s when they end.
 */
internal class IndentBlocks(private val regions: MutableList<FoldRegion>) {
    private var lines = IntArray(INITIAL_DEPTH)
    private var indents = IntArray(INITIAL_DEPTH)

    // the last comment that belongs to the block, or a block within it
    private var lasts = IntArray(INITIAL_DEPTH)

    // the last line of the statement that starts the block, it only folds when there is more
    private var heads = IntArray(INITIAL_DEPTH)
    private var size = 0

    // the last line with code so far, which is within every open block
    private var lastText = -1

    /** a line that starts a statement with the [indent], it ends the blocks that are not indented less */
    fun begin(line: Int, indent: Int) {
        close(indent)
        if (size == lines.size) {
            lines = lines.copyOf(size * 2)
            indents = indents.copyOf(size * 2)
            lasts = lasts.copyOf(size * 2)
            heads = heads.copyOf(size * 2)
        }
        lines[size] = line
        indents[size] = indent
        lasts[size] = line
        heads[size] = line
        size++
    }

    /** the statement of the innermost block goes on in the [line] */
    fun continueHead(line: Int) {
        if (size > 0) heads[size - 1] = line
        lastText = line
    }

    /** the [line] has code, which belongs to the open blocks */
    fun text(line: Int) {
        lastText = line
    }

    /** a line with only a comment, it belongs to the blocks indented less than it */
    fun comment(line: Int, indent: Int) {
        var index = size - 1
        while (index >= 0 && indents[index] >= indent) index--
        if (index >= 0) lasts[index] = line
    }

    /** ends the blocks that are indented at least as deep as the [indent] */
    fun close(indent: Int) {
        while (size > 0 && indents[size - 1] >= indent) pop()
    }

    /** ends all blocks */
    fun closeAll() {
        while (size > 0) pop()
    }

    private fun pop() {
        size--
        val start = lines[size]
        val end = maxOf(lastText, lasts[size])
        if (end > heads[size]) regions += FoldRegion(start, end)
        if (size > 0 && lasts[size - 1] < end) lasts[size - 1] = end
    }

    private companion object {
        const val INITIAL_DEPTH = 16
    }
}

/** Reads the lines of a text without allocating. */
internal object IndentLines {
    /** @return where the line that starts at [start] ends, without the line break and a carriage return before it */
    fun lineEnd(text: String, start: Int): Int {
        var end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        if (end > start && text[end - 1] == '\r') end--
        return end
    }

    /** @return where the line after the one that starts at [start] starts, past the end of the text after the last */
    fun nextLineStart(text: String, start: Int): Int =
        text.indexOf('\n', start).let { if (it < 0) text.length + 1 else it + 1 }

    /** @return the first position from [start] that is no space or tab, [end] if there is none */
    fun firstNonSpace(text: String, start: Int, end: Int): Int {
        var index = start
        while (index < end && (text[index] == ' ' || text[index] == '\t')) index++
        return index
    }

    /** @return the column of [first], when the spaces and tabs from [start] to it are expanded with tab stops */
    fun indentColumns(text: String, start: Int, first: Int, tabWidth: Int, column: Int = 0): Int {
        var columns = column
        for (index in start until first) {
            columns = if (text[index] == '\t') (columns / tabWidth + 1) * tabWidth else columns + 1
        }
        return columns
    }

    /** @return the [regions] with only the longest one of those that start on the same line */
    fun longestPerStartLine(regions: MutableList<FoldRegion>): List<FoldRegion> {
        if (regions.size < 2) return regions
        regions.sortWith(compareBy<FoldRegion> { it.startLine }.thenByDescending { it.endLine })
        val result = ArrayList<FoldRegion>(regions.size)
        for (region in regions) {
            if (result.isEmpty() || result.last().startLine != region.startLine) result += region
        }
        return result
    }
}
