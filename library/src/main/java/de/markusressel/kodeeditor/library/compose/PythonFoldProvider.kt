package de.markusressel.kodeeditor.library.compose

/**
 * Folds Python:
 * - a statement folds the statements below it that are indented deeper, like the body of a `def`, `class`, `if` or
 *   `with`. Blank lines and comments don't end a block, blank lines at its end stay visible
 * - a string in triple quotes over several lines, like a docstring. What is in it is no code
 * - brackets that are opened and closed in different lines. A line that starts with the closing bracket stays visible.
 *   The lines within brackets, and those after a backslash at the end of a line, go on the statement before them
 * - two or more `import` and `from … import` statements in a row, blank lines and comments between them included
 *
 * Tabs move the indentation to the next multiple of 8 columns, like Python does.
 */
object PythonFoldProvider : KodeEditorFoldProvider {
    private const val TAB_WIDTH = 8
    private const val NO_QUOTE = ' '
    private const val TRIPLE = 3
    private const val INITIAL_BRACKETS = 16
    private const val MIN_IMPORTS = 2

    override fun foldRegions(text: String): List<FoldRegion> = with(IndentLines) {
        val regions = ArrayList<FoldRegion>()
        val blocks = IndentBlocks(regions)
        // the lines of the brackets that are open
        var brackets = IntArray(INITIAL_BRACKETS)
        var depth = 0
        var quote = NO_QUOTE
        var triple = false
        var stringStart = -1
        var backslash = false
        var lastText = -1

        var importStart = -1
        var importEnd = -1
        var importIndent = -1
        var imports = 0
        var inImport = false

        fun closeImports() {
            if (imports >= MIN_IMPORTS && importEnd > importStart) regions += FoldRegion(importStart, importEnd)
            importStart = -1
            imports = 0
        }

        var line = 0
        var start = 0
        while (start <= text.length) {
            val end = lineEnd(text, start)
            val first = firstNonSpace(text, start, end)
            val continued = quote != NO_QUOTE || depth > 0 || backslash
            backslash = false
            if (!continued) {
                if (first == end) {
                    start = nextLineStart(text, start)
                    line++
                    continue
                }
                val indent = indentColumns(text, start, first, TAB_WIDTH)
                if (text[first] == '#') {
                    blocks.comment(line, indent)
                    start = nextLineStart(text, start)
                    line++
                    continue
                }
                blocks.begin(line, indent)
                inImport = startsWithWord(text, first, end, "import") || startsWithWord(text, first, end, "from")
                if (inImport && importStart >= 0 && indent == importIndent) {
                    imports++
                } else {
                    closeImports()
                    if (inImport) {
                        importStart = line
                        importIndent = indent
                        imports = 1
                    }
                }
            }
            if (first < end) {
                if (continued) blocks.continueHead(line) else blocks.text(line)
                lastText = line
                if (inImport) importEnd = line
            }

            // what is in the line, from the first character that is no space
            var onlyClosing = true
            var index = first
            while (index < end) {
                val character = text[index]
                if (quote != NO_QUOTE) {
                    if (character == '\\') {
                        index += 2
                        continue
                    }
                    if (character == quote && (!triple || isTriple(text, index, end, quote))) {
                        if (triple) {
                            if (line > stringStart) regions += FoldRegion(stringStart, line)
                            index += TRIPLE
                        } else {
                            index++
                        }
                        quote = NO_QUOTE
                        continue
                    }
                    index++
                    continue
                }
                when (character) {
                    '#' -> break
                    '"', '\'' -> {
                        triple = isTriple(text, index, end, character)
                        quote = character
                        stringStart = line
                        index += if (triple) TRIPLE else 1
                        onlyClosing = false
                        continue
                    }

                    '(', '[', '{' -> {
                        if (depth == brackets.size) brackets = brackets.copyOf(depth * 2)
                        brackets[depth++] = line
                    }

                    ')', ']', '}' -> if (depth > 0) {
                        val open = brackets[--depth]
                        val last = if (onlyClosing) line - 1 else line
                        if (last > open) regions += FoldRegion(open, last)
                    }

                    '\\' -> if (index == end - 1) backslash = true
                }
                if (character != ')' && character != ']' && character != '}' && character != ' ' &&
                    character != '\t'
                ) {
                    onlyClosing = false
                }
                index++
            }
            // a backslash at the end of the line goes on with a string in single quotes, else the string ends here
            if (quote != NO_QUOTE && !triple && index <= end) quote = NO_QUOTE

            start = nextLineStart(text, start)
            line++
        }

        blocks.closeAll()
        closeImports()
        if (quote != NO_QUOTE && triple && lastText > stringStart) regions += FoldRegion(stringStart, lastText)
        for (bracket in 0 until depth) {
            if (lastText > brackets[bracket]) regions += FoldRegion(brackets[bracket], lastText)
        }
        longestPerStartLine(regions)
    }

    private fun isTriple(text: String, index: Int, end: Int, quote: Char) =
        index + 2 < end && text[index + 1] == quote && text[index + 2] == quote

    /** @return whether the line has the [word] at [start], followed by a space */
    private fun startsWithWord(text: String, start: Int, end: Int, word: String): Boolean {
        val after = start + word.length
        return after < end && text.startsWith(word, start) && (text[after] == ' ' || text[after] == '\t')
    }
}
