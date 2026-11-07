package de.markusressel.kodeeditor.library.compose

/**
 * Folds OCaml: `struct`, `sig`, `object` and `begin` until their `end`, and `( … )`, `[ … ]`, `[| … |]` and `{ … }`,
 * comments and strings that span several lines. The line of the end is hidden too, unless more follows on it.
 * Definitions with `let`, `type`, `module`, `and` and the like at the start of a line fold until the last line of code
 * before the next one, so comments between them stay visible.
 *
 * Comments nest, and strings, quoted strings like `{id|…|id}` and characters within code and comments are skipped. An
 * end without a start is ignored, and one that closes something further out closes what is open within it too.
 */
object OCamlFoldProvider : KodeEditorFoldProvider {
    override fun foldRegions(text: String): List<FoldRegion> = Scanner(text).run()

    private class Scanner(private val text: String) {
        private val regions = ArrayList<FoldRegion>()
        private var openLines = IntArray(INITIAL_DEPTH)
        private var openKinds = IntArray(INITIAL_DEPTH)
        private var depth = 0
        private var index = 0
        private var line = 0
        private var lineStart = 0
        private var lastCode = -1
        private var definition = -1

        fun run(): List<FoldRegion> {
            while (index < text.length) {
                val character = text[index]
                when {
                    character == '\n' -> newLine(index + 1)
                    character == ' ' || character == '\t' || character == '\r' -> index++
                    character == '(' && next() == '*' -> comment()
                    else -> {
                        code(character)
                        lastCode = line
                    }
                }
            }
            closeDefinition()
            return outermost(regions)
        }

        private fun newLine(start: Int) {
            line++
            index = start
            lineStart = start
        }

        private fun next() = if (index + 1 < text.length) text[index + 1] else ' '

        private fun code(character: Char) {
            when {
                character == '"' -> string()
                character == '{' && quotedString() -> Unit
                character == '{' -> open(BRACE, 1)
                character == '[' && next() == '|' -> open(ARRAY, 2)
                character == '[' -> open(BRACKET, 1)
                character == '(' -> open(PAREN, 1)
                character == '|' && next() == ']' -> close(ARRAY, 2)
                character == ']' -> close(BRACKET, 1)
                character == ')' -> close(PAREN, 1)
                character == '}' -> close(BRACE, 1)
                character == '\'' -> skipCharacter()
                character.isLetter() || character == '_' -> word()
                character.isDigit() -> while (index < text.length && isIdentifier(text[index])) index++
                else -> index++
            }
        }

        private fun word() {
            val start = index
            while (index < text.length && isIdentifier(text[index])) index++
            if (start == lineStart && DEFINITIONS.any { isWord(start, index, it) }) {
                closeDefinition()
                definition = line
            }
            when {
                BLOCKS.any { isWord(start, index, it) } -> push(BLOCK)
                isWord(start, index, "end") -> {
                    index = start
                    close(BLOCK, 3)
                }
            }
        }

        private fun isWord(start: Int, end: Int, word: String) =
            end - start == word.length && text.regionMatches(start, word, 0, word.length)

        private fun closeDefinition() {
            if (definition >= 0 && lastCode > definition) regions += FoldRegion(definition, lastCode)
            definition = -1
        }

        private fun comment() {
            // the lines where the comment and those within it start
            var startLines = IntArray(INITIAL_DEPTH)
            startLines[0] = line
            index += 2
            var nesting = 1
            while (index < text.length) {
                val character = text[index]
                when {
                    character == '\n' -> newLine(index + 1)
                    character == '(' && next() == '*' -> {
                        if (nesting == startLines.size) startLines = startLines.copyOf(nesting * 2)
                        startLines[nesting++] = line
                        index += 2
                    }

                    character == '*' && next() == ')' -> {
                        index += 2
                        add(startLines[--nesting])
                        if (nesting == 0) return
                    }

                    character == '"' -> string()
                    character == '{' && quotedString() -> Unit
                    character == '\'' -> skipCharacter()
                    else -> index++
                }
            }
        }

        private fun string() {
            val startLine = line
            index++
            while (index < text.length) {
                when (text[index]) {
                    '\n' -> newLine(index + 1)
                    '\\' -> if (next() == '\n') newLine(index + 2) else index += 2
                    '"' -> {
                        index++
                        add(startLine)
                        return
                    }

                    else -> index++
                }
            }
        }

        /** skips a string like `{id|…|id}` if one starts here. @return whether it did */
        private fun quotedString(): Boolean {
            val idStart = index + 1
            var idEnd = idStart
            while (idEnd < text.length && (text[idEnd] in 'a'..'z' || text[idEnd] == '_')) idEnd++
            if (idEnd >= text.length || text[idEnd] != '|') return false
            val idLength = idEnd - idStart
            val startLine = line
            index = idEnd + 1
            while (index < text.length) {
                val character = text[index]
                if (character == '\n') {
                    newLine(index + 1)
                    continue
                }
                index++
                if (character == '|' && text.regionMatches(index, text, idStart, idLength) &&
                    index + idLength < text.length && text[index + idLength] == '}'
                ) {
                    index += idLength + 1
                    add(startLine)
                    return true
                }
            }
            return true
        }

        /** skips a character like `'a'` or `'\n'`, or the quote of a type variable like `'a` */
        private fun skipCharacter() {
            if (index > 0 && isIdentifier(text[index - 1])) {
                index++
                return
            }
            if (next() == '\\') {
                // what follows the backslash is at least one character, which can be a quote
                var close = index + 3
                val limit = minOf(text.length, index + MAX_CHARACTER_LENGTH)
                while (close < limit && text[close] != '\'' && text[close] != '\n') close++
                index = if (close < limit && text[close] == '\'') close + 1 else index + 1
                return
            }
            index += if (index + 2 < text.length && text[index + 2] == '\'' && next() != '\n') 3 else 1
        }

        private fun open(kind: Int, length: Int) {
            push(kind)
            index += length
        }

        private fun push(kind: Int) {
            if (depth == openLines.size) {
                openLines = openLines.copyOf(depth * 2)
                openKinds = openKinds.copyOf(depth * 2)
            }
            openLines[depth] = line
            openKinds[depth] = kind
            depth++
        }

        private fun close(kind: Int, length: Int) {
            index += length
            val lowest = maxOf(0, depth - MAX_SEARCH)
            for (position in depth - 1 downTo lowest) {
                if (openKinds[position] == kind) {
                    depth = position
                    add(openLines[position])
                    return
                }
            }
        }

        /** adds a region from [startLine] to what was just passed */
        private fun add(startLine: Int) {
            var after = index
            while (after < text.length && text[after].let { it == ' ' || it == '\t' || it == '\r' || it == ';' }) {
                after++
            }
            val ends = after == text.length || text[after] == '\n' ||
                text[after] == '(' && after + 1 < text.length && text[after + 1] == '*'
            val endLine = if (ends) line else line - 1
            if (endLine > startLine) regions += FoldRegion(startLine, endLine)
        }
    }

    private fun isIdentifier(character: Char) = character.isLetterOrDigit() || character == '_' || character == '\''

    /** @return one region per line, the longest one */
    private fun outermost(regions: List<FoldRegion>): List<FoldRegion> {
        if (regions.size < 2) return regions
        val result = ArrayList<FoldRegion>(regions.size)
        for (region in regions.sortedWith(compareBy<FoldRegion> { it.startLine }.thenByDescending { it.endLine })) {
            if (result.isEmpty() || result.last().startLine != region.startLine) result += region
        }
        return result
    }

    private const val PAREN = 0
    private const val BRACKET = 1
    private const val ARRAY = 2
    private const val BRACE = 3
    private const val BLOCK = 4

    private const val INITIAL_DEPTH = 16

    /** how many open blocks and brackets an end looks through */
    private const val MAX_SEARCH = 256

    /** how long a character with an escape like `'\u{1F600}'` can be */
    private const val MAX_CHARACTER_LENGTH = 12

    private val BLOCKS = listOf("struct", "sig", "object", "begin")
    private val DEFINITIONS = listOf(
        "let", "and", "type", "module", "class", "exception", "external", "open", "include", "val",
    )
}
