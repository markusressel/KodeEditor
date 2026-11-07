package de.markusressel.kodeeditor.library.compose

/**
 * Folds XML: an element from the line of its start tag to the line of its end tag, and start tags, comments, CDATA
 * sections, processing instructions and document type declarations that span several lines. The last line is hidden
 * too, unless more follows on it. Names are case-sensitive.
 *
 * An end tag closes the nearest open element of its name, the elements opened within that stay without a region. An
 * end tag without a start tag is ignored, and so are elements that are still open at the end. Text that does not start
 * with `<?xml` may be HTML: void elements like `<br>` need no end tag there, and `script` and `style` contain text.
 */
object XmlFoldProvider : KodeEditorFoldProvider {
    override fun foldRegions(text: String): List<FoldRegion> = Scanner(text).run()

    /** an element whose end tag was not found yet */
    private class Open(val nameStart: Int, val nameEnd: Int, val line: Int, val tagEndLine: Int)

    private class Scanner(private val text: String) {
        private val regions = ArrayList<FoldRegion>()
        private val open = ArrayList<Open>()
        private val html = !text.startsWith("<?xml")
        private var index = 0
        private var line = 0

        fun run(): List<FoldRegion> {
            while (index < text.length) {
                when (text[index]) {
                    '\n' -> {
                        line++
                        index++
                    }

                    '<' -> markup()
                    else -> index++
                }
            }
            for (element in open) addTagRegion(element)
            return outermost(regions)
        }

        private fun markup() {
            val startLine = line
            when {
                text.startsWith("<!--", index) -> if (skipPast("-->", index + 4)) add(startLine)
                text.startsWith("<![CDATA[", index) -> if (skipPast("]]>", index + 9)) add(startLine)
                text.startsWith("<?", index) -> if (skipPast("?>", index + 2)) add(startLine)
                text.startsWith("<!", index) -> if (declaration()) add(startLine)
                text.startsWith("</", index) -> endTag()
                index + 1 < text.length && isNameStart(text[index + 1]) -> startTag()
                else -> index++
            }
        }

        /** moves past the next [terminator] after [from], or to the end. @return whether it was found */
        private fun skipPast(terminator: String, from: Int): Boolean {
            val found = text.indexOf(terminator, from)
            moveTo(if (found < 0) text.length else found + terminator.length)
            return found >= 0
        }

        private fun moveTo(target: Int) {
            while (index < target) {
                if (text[index] == '\n') line++
                index++
            }
        }

        /** skips a declaration like `<!DOCTYPE …>` with its internal subset. @return whether it ended */
        private fun declaration(): Boolean {
            index += 2
            var depth = 0
            var quote = NO_QUOTE
            while (index < text.length) {
                val character = text[index]
                when {
                    character == '\n' -> line++
                    quote != NO_QUOTE -> if (character == quote) quote = NO_QUOTE
                    character == '"' || character == '\'' -> quote = character
                    character == '<' && text.startsWith("<!--", index) -> {
                        if (!skipPast("-->", index + 4)) return false
                        continue
                    }

                    character == '[' -> depth++
                    character == ']' -> if (depth > 0) depth--
                    character == '>' && depth == 0 -> {
                        index++
                        return true
                    }
                }
                index++
            }
            return false
        }

        private fun startTag() {
            val startLine = line
            val nameStart = index + 1
            index = nameStart
            while (index < text.length && isNameCharacter(text[index])) index++
            val nameEnd = index
            var selfClosing = false
            var ended = false
            while (index < text.length && !ended) {
                when (val character = text[index]) {
                    '\n' -> line++
                    '"', '\'' -> {
                        index++
                        while (index < text.length && text[index] != character) {
                            if (text[index] == '\n') line++
                            index++
                        }
                    }

                    '<' -> break
                    '>' -> {
                        ended = true
                        selfClosing = text[index - 1] == '/'
                    }
                }
                index++
            }
            if (!ended) return
            val element = Open(nameStart, nameEnd, startLine, line)
            when {
                selfClosing || html && isVoid(nameStart, nameEnd) -> addTagRegion(element)
                else -> {
                    open += element
                    if (html && (isName(nameStart, nameEnd, "script") || isName(nameStart, nameEnd, "style"))) {
                        skipRawText(nameStart, nameEnd)
                    }
                }
            }
        }

        /** moves to the end tag of the element whose name is at [nameStart], or to the end */
        private fun skipRawText(nameStart: Int, nameEnd: Int) {
            var from = index
            while (true) {
                val found = text.indexOf("</", from)
                if (found < 0) {
                    moveTo(text.length)
                    return
                }
                if (text.regionMatches(found + 2, text, nameStart, nameEnd - nameStart, ignoreCase = true)) {
                    moveTo(found)
                    return
                }
                from = found + 2
            }
        }

        private fun endTag() {
            val nameStart = index + 2
            index = nameStart
            while (index < text.length && isNameCharacter(text[index])) index++
            val nameEnd = index
            while (index < text.length && text[index] != '>' && text[index] != '<') {
                if (text[index] == '\n') line++
                index++
            }
            if (index < text.length && text[index] == '>') index++
            val length = nameEnd - nameStart
            val lowest = maxOf(0, open.size - MAX_SEARCH)
            for (position in open.lastIndex downTo lowest) {
                val element = open[position]
                if (element.nameEnd - element.nameStart == length &&
                    text.regionMatches(element.nameStart, text, nameStart, length)
                ) {
                    for (inner in open.lastIndex downTo position + 1) addTagRegion(open.removeAt(inner))
                    open.removeAt(position)
                    add(element.line)
                    return
                }
            }
        }

        /** adds the start tag of the [element] if it spans several lines */
        private fun addTagRegion(element: Open) {
            if (element.tagEndLine > element.line) regions += FoldRegion(element.line, element.tagEndLine)
        }

        /** adds a region from [startLine] to what was just passed */
        private fun add(startLine: Int) {
            var after = index
            while (after < text.length && (text[after] == ' ' || text[after] == '\t' || text[after] == '\r')) after++
            val endLine = if (after == text.length || text[after] == '\n') line else line - 1
            if (endLine > startLine) regions += FoldRegion(startLine, endLine)
        }

        private fun isVoid(nameStart: Int, nameEnd: Int) = VOID_ELEMENTS.any { isName(nameStart, nameEnd, it) }

        private fun isName(nameStart: Int, nameEnd: Int, name: String) =
            nameEnd - nameStart == name.length && text.regionMatches(nameStart, name, 0, name.length, ignoreCase = true)
    }

    private fun isNameStart(character: Char) = character.isLetter() || character == '_' || character == ':'

    private fun isNameCharacter(character: Char) =
        !character.isWhitespace() && character != '/' && character != '>' && character != '<' &&
            character != '=' && character != '"' && character != '\''

    /** @return one region per line, the longest one */
    private fun outermost(regions: List<FoldRegion>): List<FoldRegion> {
        if (regions.size < 2) return regions
        val result = ArrayList<FoldRegion>(regions.size)
        for (region in regions.sortedWith(compareBy<FoldRegion> { it.startLine }.thenByDescending { it.endLine })) {
            if (result.isEmpty() || result.last().startLine != region.startLine) result += region
        }
        return result
    }

    private const val NO_QUOTE = ' '

    /** how many open elements an end tag looks through for its start tag */
    private const val MAX_SEARCH = 256

    private val VOID_ELEMENTS = listOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )
}
