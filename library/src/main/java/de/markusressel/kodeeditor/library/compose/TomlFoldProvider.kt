package de.markusressel.kodeeditor.library.compose

/**
 * Folds TOML: a `[table]` or `[[array of tables]]` header hides everything until the next header, except blank lines
 * at the end. Arrays, inline tables and multi-line strings that span several lines fold too, with the line of their end
 * unless more follows on it. Comments and strings are skipped.
 *
 * An array that is still open does not hide the headers after it: a line that starts with `[` and only a bare key ends
 * it.
 */
object TomlFoldProvider : KodeEditorFoldProvider {
    override fun foldRegions(text: String): List<FoldRegion> = Scanner(text).run()

    private class Scanner(private val text: String) {
        private val regions = ArrayList<FoldRegion>()
        private var openLines = IntArray(INITIAL_DEPTH)
        private var openCharacters = CharArray(INITIAL_DEPTH)
        private var depth = 0
        private var index = 0
        private var line = 0
        private var lineStart = 0
        private var lineHasText = false
        private var lastWithText = -1
        private var header = -1

        fun run(): List<FoldRegion> {
            while (index < text.length) {
                val character = text[index]
                if (character == '\n') {
                    line++
                    index++
                    lineStart = index
                    lineHasText = false
                    continue
                }
                if (character == ' ' || character == '\t' || character == '\r') {
                    index++
                    continue
                }
                if (!lineHasText && character == '[' && isHeader()) {
                    closeHeader()
                    header = line
                    depth = 0
                    lastWithText = line
                    skipLine()
                    continue
                }
                lineHasText = true
                lastWithText = line
                when (character) {
                    '#' -> skipLine()
                    '"' -> if (text.startsWith("\"\"\"", index)) multiLineString('"') else string('"')
                    '\'' -> if (text.startsWith("'''", index)) multiLineString('\'') else string('\'')
                    '[', '{' -> {
                        push(character)
                        index++
                    }

                    ']' -> close('[')
                    '}' -> close('{')
                    else -> index++
                }
            }
            closeHeader()
            return outermost(regions)
        }

        /** @return whether the `[` at the position starts a header */
        private fun isHeader(): Boolean {
            if (depth == 0) return true
            if (index != lineStart) return false
            // within an array that is still open only something like [a.b-c] is a header, not a value like [1, 2]
            var position = index + 1
            if (position < text.length && text[position] == '[') position++
            var hasLetter = false
            while (position < text.length) {
                val character = text[position]
                when {
                    character.isLetter() -> hasLetter = true
                    character.isDigit() || character == '_' || character == '-' || character == '.' ||
                        character == ' ' || character == '\t' -> Unit

                    else -> break
                }
                position++
            }
            if (!hasLetter || position >= text.length || text[position] != ']') return false
            while (position < text.length && text[position] == ']') position++
            while (position < text.length && text[position].let { it == ' ' || it == '\t' || it == '\r' }) position++
            return position == text.length || text[position] == '\n' || text[position] == '#'
        }

        private fun closeHeader() {
            if (header >= 0 && lastWithText > header) regions += FoldRegion(header, lastWithText)
            header = -1
        }

        private fun skipLine() {
            val end = text.indexOf('\n', index)
            index = if (end < 0) text.length else end
        }

        /** skips a string that ends with the line */
        private fun string(quote: Char) {
            index++
            while (index < text.length) {
                val character = text[index]
                if (character == '\n') return
                index++
                if (character == quote) return
                if (character == '\\' && quote == '"' && index < text.length && text[index] != '\n') index++
            }
        }

        private fun multiLineString(quote: Char) {
            val startLine = line
            index += 3
            while (index < text.length) {
                val character = text[index]
                when {
                    character == '\n' -> {
                        line++
                        lineStart = index + 1
                    }

                    character == quote && text.startsWith(if (quote == '"') "\"\"\"" else "'''", index) -> {
                        // up to two more quotes are part of the content
                        index += 3
                        var extra = 0
                        while (extra < 2 && index < text.length && text[index] == quote) {
                            index++
                            extra++
                        }
                        lastWithText = line
                        add(startLine)
                        return
                    }

                    character == '\\' && quote == '"' -> {
                        if (index + 1 < text.length && text[index + 1] == '\n') {
                            line++
                            lineStart = index + 2
                        }
                        index++
                    }

                    character != ' ' && character != '\t' && character != '\r' -> lastWithText = line
                }
                index++
            }
        }

        private fun push(character: Char) {
            if (depth == openLines.size) {
                openLines = openLines.copyOf(depth * 2)
                openCharacters = openCharacters.copyOf(depth * 2)
            }
            openLines[depth] = line
            openCharacters[depth] = character
            depth++
        }

        private fun close(opening: Char) {
            index++
            val lowest = maxOf(0, depth - MAX_SEARCH)
            for (position in depth - 1 downTo lowest) {
                if (openCharacters[position] == opening) {
                    depth = position
                    add(openLines[position])
                    return
                }
            }
        }

        /** adds a region from [startLine] to what was just passed */
        private fun add(startLine: Int) {
            var after = index
            while (after < text.length && text[after].let { it == ' ' || it == '\t' || it == '\r' || it == ',' }) {
                after++
            }
            val ends = after == text.length || text[after] == '\n' || text[after] == '#'
            val endLine = if (ends) line else line - 1
            if (endLine > startLine) regions += FoldRegion(startLine, endLine)
        }
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

    private const val INITIAL_DEPTH = 16

    /** how many open brackets a closing one looks through */
    private const val MAX_SEARCH = 256
}
