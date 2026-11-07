package de.markusressel.kodeeditor.library.compose

/**
 * Folds YAML:
 * - a key folds the lines below it that are indented deeper, which is its value. An item of a sequence folds the lines
 *   that are indented deeper than its `-`, and a sequence may be indented as deep as the key it belongs to. Blank lines
 *   and comments don't end a value, blank lines at its end stay visible
 * - block scalars that start with `|` or `>`, also with indicators like `|-` or `>+2`. What is in them is text
 * - each document that starts with `---`, up to the next `---` or `...`
 *
 * A `#` only starts a comment at the start of a line or after a space, and not in a quoted key.
 */
object YamlFoldProvider : KodeEditorFoldProvider {
    private const val TAB_WIDTH = 4
    private const val NO_SCALAR = Int.MIN_VALUE
    private const val MARKER_LENGTH = 3

    override fun foldRegions(text: String): List<FoldRegion> = with(IndentLines) {
        val regions = ArrayList<FoldRegion>()
        // indentations are doubled, so an item of a sequence is deeper than a key with the same indentation
        val blocks = IndentBlocks(regions)
        // the indentation that the lines of the block scalar are deeper than
        var scalarParent = NO_SCALAR
        var document = -1
        var lastText = -1

        fun closeDocument() {
            if (document >= 0 && lastText > document) regions += FoldRegion(document, lastText)
            document = -1
        }

        var line = 0
        var start = 0
        while (start <= text.length) {
            val end = lineEnd(text, start)
            val first = firstNonSpace(text, start, end)
            if (first < end) {
                val indent = indentColumns(text, start, first, TAB_WIDTH)
                if (scalarParent != NO_SCALAR && indent > scalarParent) {
                    blocks.text(line)
                    lastText = line
                } else {
                    scalarParent = NO_SCALAR
                    if (indent == 0 && isMarker(text, first, end, '-')) {
                        blocks.closeAll()
                        closeDocument()
                        document = line
                    } else if (indent == 0 && isMarker(text, first, end, '.')) {
                        blocks.closeAll()
                        closeDocument()
                    } else if (text[first] == '#') {
                        blocks.comment(line, indent * 2)
                        lastText = line
                    } else {
                        scalarParent = node(text, first, end, indent, line, blocks)
                        blocks.text(line)
                        lastText = line
                    }
                }
            }
            start = nextLineStart(text, start)
            line++
        }
        blocks.closeAll()
        closeDocument()
        longestPerStartLine(regions)
    }

    /**
     * Opens the blocks of the line that has a node at [first], one for each `-` and one for what comes after them.
     *
     * @return the indentation the lines of a block scalar that starts in this line are deeper than, [NO_SCALAR] if
     * there is none
     */
    private fun node(text: String, first: Int, end: Int, indent: Int, line: Int, blocks: IndentBlocks): Int {
        with(IndentLines) {
            var position = first
            var column = indent
            var dash = -1
            while (text[position] == '-' && (position + 1 == end || isSpace(text[position + 1]))) {
                blocks.begin(line, column * 2 + 1)
                dash = column
                val next = firstNonSpace(text, position + 1, end)
                column = indentColumns(text, position, next, TAB_WIDTH, column)
                position = next
                if (position == end) return NO_SCALAR
            }
            if (text[position] == '#') return NO_SCALAR
            blocks.begin(line, column * 2)

            val separator = keySeparator(text, position, end)
            val value = firstNonSpace(text, if (separator >= 0) separator + 1 else position, end)
            return when {
                !isBlockScalar(text, value, end) -> NO_SCALAR
                separator >= 0 -> column
                dash >= 0 -> dash
                else -> NO_SCALAR
            }
        }
    }

    /** @return where the `:` after the key that starts at [start] is, -1 if there is none */
    private fun keySeparator(text: String, start: Int, end: Int): Int {
        var index = start
        val quote = text[start]
        if (quote == '"' || quote == '\'') {
            index++
            while (index < end) {
                val character = text[index]
                if (quote == '"' && character == '\\') {
                    index++
                } else if (character == quote) {
                    if (quote == '\'' && index + 1 < end && text[index + 1] == '\'') {
                        index++
                    } else {
                        break
                    }
                }
                index++
            }
            index++
        }
        while (index < end) {
            val character = text[index]
            if (character == '#' && index > start && isSpace(text[index - 1])) return -1
            if (character == ':' && (index + 1 == end || isSpace(text[index + 1]))) return index
            index++
        }
        return -1
    }

    /** @return whether the value at [start] is the start of a block scalar, like `|`, `>-` or `|2+  # comment` */
    private fun isBlockScalar(text: String, start: Int, end: Int): Boolean {
        if (start >= end || (text[start] != '|' && text[start] != '>')) return false
        var index = start + 1
        while (index < end && (text[index] == '+' || text[index] == '-' || text[index] in '1'..'9')) index++
        if (index == end) return true
        if (!isSpace(text[index])) return false
        index = IndentLines.firstNonSpace(text, index, end)
        return index == end || text[index] == '#'
    }

    /** @return whether the line is `---` or `...`, alone or followed by a space */
    private fun isMarker(text: String, first: Int, end: Int, character: Char): Boolean {
        if (first + MARKER_LENGTH > end) return false
        for (index in first until first + MARKER_LENGTH) if (text[index] != character) return false
        return first + MARKER_LENGTH == end || isSpace(text[first + MARKER_LENGTH])
    }

    private fun isSpace(character: Char) = character == ' ' || character == '\t'
}
