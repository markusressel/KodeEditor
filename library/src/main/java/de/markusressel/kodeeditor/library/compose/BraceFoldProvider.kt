package de.markusressel.kodeeditor.library.compose

/**
 * How a string of a language starts and ends, for [BraceFoldProvider]. Brackets and comments in strings are ignored.
 */
sealed interface BraceStringSyntax {

    /**
     * A string between [start] and [end], like `"a"`, `'a'`, `` `a` `` or `@"a"`
     *
     * @param start what starts the string
     * @param end what ends the string
     * @param escape the character that makes the next one part of the string, null if there is none
     * @param multiLine whether the string can go over more lines, otherwise it ends with its line
     * @param doubledEnd whether [end] twice is part of the string, like `""` in `@"a""b"` of C#
     */
    data class Quoted(
        val start: String,
        val end: String = start,
        val escape: Char? = '\\',
        val multiLine: Boolean = false,
        val doubledEnd: Boolean = false,
    ) : BraceStringSyntax {
        init {
            require(start.isNotEmpty() && end.isNotEmpty()) { "start and end must not be empty" }
        }
    }

    /**
     * A string that starts with [prefix] and at least [minLength] [quote]s and can go over more lines, like `"""a"""`.
     * It ends with a run of as many quotes, the whole run belongs to the string.
     *
     * @param quote the quote that is repeated
     * @param minLength how many quotes start the string at least
     * @param sameLength whether the string ends with as many quotes as it started with, like the raw strings of C#,
     * otherwise [minLength] quotes end it
     * @param escape the character that makes the next one part of the string, null if there is none
     * @param prefix what comes before the quotes, like `r` in `r"""a"""` of Dart
     */
    data class QuoteRun(
        val quote: Char = '"',
        val minLength: Int = 3,
        val sameLength: Boolean = false,
        val escape: Char? = null,
        val prefix: String = "",
    ) : BraceStringSyntax {
        init {
            require(minLength >= 2) { "minLength must be at least 2" }
        }
    }

    /**
     * A string that starts with [prefix], some `#` and [quote] and ends with [quote] and as many `#`, like `r#"a"#` of
     * Rust or `#"a"#` of Swift. There are no escapes.
     *
     * @param prefix what comes before the `#`, like `r`
     * @param quote what comes after the `#` at the start and before them at the end
     * @param minHashes how many `#` there have to be at least
     * @param multiLine whether the string can go over more lines, otherwise it ends with its line
     */
    data class Hashed(
        val prefix: String,
        val quote: String = "\"",
        val minHashes: Int = 0,
        val multiLine: Boolean = true,
    ) : BraceStringSyntax {
        init {
            require(quote.isNotEmpty()) { "quote must not be empty" }
            require(prefix.isNotEmpty() || minHashes > 0) { "a string without prefix needs at least one #" }
        }
    }

    /** A raw string of C++ like `R"x(a)x"`, also with the prefixes `L`, `u`, `U` and `u8` */
    data object CppRaw : BraceStringSyntax
}

/**
 * Folds languages with curly brackets, like C, Java or Kotlin, in one pass over the text:
 *
 * - matching brackets whose lines are apart, the opening line stays visible
 * - comments over more lines
 * - runs of at least two lines that start with one of [importPrefixes], blank lines and line comments between them
 * belong to the run
 * - with [preprocessorFolds] `#if`, `#ifdef` and `#ifndef` until `#endif` and `#region` until `#endregion`
 *
 * The line with the closing bracket is hidden too, unless code other than `)`, `]`, `}`, `;` and `,` follows the
 * bracket on that line, like in `} else {`. Then that line stays visible. Brackets in strings and comments are
 * ignored, a closing bracket without its opening one is ignored too and brackets that are not closed fold nothing.
 *
 * Several regions that start in the same line are joined into the longest one.
 *
 * @param lineComments what starts a comment until the end of the line
 * @param blockCommentStart what starts a comment that can go over more lines, null if there is none
 * @param blockCommentEnd what ends such a comment
 * @param nestedBlockComments whether such comments can contain more of them, like in Kotlin or Rust
 * @param strings the kinds of strings, the first one that matches is used, so longer ones have to come first
 * @param charLiterals whether `'` starts a character like `'a'` or `'\n'`. Other uses like `'a` stay code
 * @param foldedBrackets the pairs of brackets that fold, out of `{}`, `[]` and `()`. All three are matched in any case
 * @param importPrefixes what lines start with that are folded as a group, like `import `
 * @param preprocessorFolds whether `#if … #endif` and `#region … #endregion` fold
 */
class BraceFoldProvider(
    private val lineComments: List<String> = listOf("//"),
    private val blockCommentStart: String? = "/*",
    private val blockCommentEnd: String = "*/",
    private val nestedBlockComments: Boolean = false,
    private val strings: List<BraceStringSyntax> = listOf(BraceStringSyntax.Quoted("\"")),
    private val charLiterals: Boolean = true,
    foldedBrackets: String = "{}[]()",
    private val importPrefixes: List<String> = emptyList(),
    private val preprocessorFolds: Boolean = false,
) : KodeEditorFoldProvider {

    private val foldsBracket = BooleanArray(BRACKET_KINDS)

    // arrays, so the loops over them allocate nothing
    private val lineCommentArray = lineComments.toTypedArray()
    private val stringArray = strings.toTypedArray()
    private val importPrefixArray = importPrefixes.toTypedArray()

    // characters that might start a comment, string or character, everything else is skipped quickly
    private val startCharacters = BooleanArray(ASCII)
    private var nonAsciiStart = false

    init {
        require(lineComments.none { it.isEmpty() }) { "line comments must not be empty" }
        require(blockCommentStart == null || (blockCommentStart.isNotEmpty() && blockCommentEnd.isNotEmpty())) {
            "block comments must not be empty"
        }
        require(foldedBrackets.length % 2 == 0) { "foldedBrackets has to consist of pairs" }
        for (index in foldedBrackets.indices step 2) {
            val kind = OPENING.indexOf(foldedBrackets[index])
            require(kind >= 0 && CLOSING[kind] == foldedBrackets[index + 1]) {
                "unknown pair of brackets ${foldedBrackets.substring(index, index + 2)}"
            }
            foldsBracket[kind] = true
        }
        lineComments.forEach { mark(it[0]) }
        blockCommentStart?.let { mark(it[0]) }
        if (charLiterals) mark('\'')
        strings.forEach { syntax ->
            when (syntax) {
                is BraceStringSyntax.Quoted -> mark(syntax.start[0])
                is BraceStringSyntax.QuoteRun -> mark(if (syntax.prefix.isEmpty()) syntax.quote else syntax.prefix[0])
                is BraceStringSyntax.Hashed -> mark(if (syntax.prefix.isEmpty()) '#' else syntax.prefix[0])
                BraceStringSyntax.CppRaw -> mark('R')
            }
        }
    }

    private fun mark(character: Char) {
        if (character.code < ASCII) startCharacters[character.code] = true else nonAsciiStart = true
    }

    override fun foldRegions(text: String): List<FoldRegion> = Scan(text).run()

    /** the state of one pass over a text */
    private inner class Scan(private val text: String) {
        private val length = text.length
        private var line = 0
        private var lineStart = 0

        // the longest hidden end of the regions by their start line, -1 if there is none
        private var bestEnd = IntArray(INITIAL_CAPACITY) { -1 }

        // the brackets that are open: the line shifted by 2 and the kind
        private val open = IntStack()
        private val openOfKind = IntArray(BRACKET_KINDS)

        // regions closed in this line whose end depends on what follows them: their start line and where they closed
        private val pendingStart = IntStack()
        private val pendingAt = IntStack()

        // where the last code in this line is that is no closing bracket, `;` or `,`
        private var lastOtherCode = -1

        private var importStart = -1
        private var importEnd = -1
        private var previousImportEnd = -1
        private var openAtLineStart = 0

        private val directives = IntStack()
        private val regionDirectives = IntStack()

        fun run(): List<FoldRegion> {
            var index = 0
            startLine(0)
            while (index < length) {
                val character = text[index]
                if (character == '\n') {
                    newLine(index)
                    index++
                    continue
                }
                index = when {
                    character == ' ' || character == '\t' || character == '\r' -> index + 1
                    isStartCharacter(character) -> special(index, character)
                    else -> {
                        code(index, character)
                        index + 1
                    }
                }
            }
            endLine()
            closeImports()
            return regions()
        }

        private fun isStartCharacter(character: Char) =
            if (character.code < ASCII) startCharacters[character.code] else nonAsciiStart

        /** handles what might start a comment, string or character, @return the position after it */
        private fun special(index: Int, character: Char): Int {
            for (comment in lineCommentArray) {
                if (text.startsWith(comment, index)) return endOfLine(index)
            }
            if (blockCommentStart != null && text.startsWith(blockCommentStart, index)) {
                return blockComment(index + blockCommentStart.length)
            }
            for (syntax in stringArray) {
                val end = string(syntax, index)
                if (end >= 0) {
                    lastOtherCode = index
                    return end
                }
            }
            if (character == '\'' && charLiterals) {
                lastOtherCode = index
                return character(index)
            }
            code(index, character)
            return index + 1
        }

        private fun code(index: Int, character: Char) {
            when (character) {
                '{', '[', '(' -> {
                    val kind = OPENING.indexOf(character)
                    open.push(line shl 2 or kind)
                    openOfKind[kind]++
                    lastOtherCode = index
                }

                '}', ']', ')' -> close(index, CLOSING.indexOf(character))
                ';', ',' -> {}
                else -> lastOtherCode = index
            }
        }

        private fun close(index: Int, kind: Int) {
            if (openOfKind[kind] == 0) return
            while (true) {
                val top = open.pop()
                val topKind = top and KIND_MASK
                openOfKind[topKind]--
                if (topKind == kind) {
                    val startLine = top ushr 2
                    if (foldsBracket[kind] && startLine < line) pending(startLine, index)
                    return
                }
            }
        }

        private fun pending(startLine: Int, at: Int) {
            pendingStart.push(startLine)
            pendingAt.push(at)
        }

        private fun newLine(index: Int) {
            endLine()
            line++
            startLine(index + 1)
        }

        private fun endLine() {
            for (pendingIndex in 0 until pendingStart.size) {
                val end = if (lastOtherCode > pendingAt[pendingIndex]) line - 1 else line
                add(pendingStart[pendingIndex], end)
            }
            pendingStart.clear()
            pendingAt.clear()
            lastOtherCode = -1
            // an import that opens a bracket, like `import (` or `use a::{`, folds by its bracket and ends the run
            if (importStart >= 0 && importEnd == line && open.size > openAtLineStart) {
                importEnd = previousImportEnd
                if (importEnd < 0) importStart = -1 else closeImports()
            }
        }

        /** looks at the start of a line that starts in code */
        private fun startLine(start: Int) {
            lineStart = start
            openAtLineStart = open.size
            var first = start
            while (first < length && (text[first] == ' ' || text[first] == '\t' || text[first] == '\r')) first++
            if (first == length || text[first] == '\n' || startsWithAny(lineCommentArray, first)) return
            if (startsWithAny(importPrefixArray, first)) {
                previousImportEnd = if (importStart < 0) -1 else importEnd
                if (importStart < 0) importStart = line
                importEnd = line
            } else {
                closeImports()
            }
            if (preprocessorFolds && text[first] == '#') directive(first + 1)
        }

        private fun startsWithAny(prefixes: Array<String>, index: Int): Boolean {
            for (prefix in prefixes) {
                if (text.startsWith(prefix, index)) return true
            }
            return false
        }

        private fun closeImports() {
            if (importStart >= 0) add(importStart, importEnd)
            importStart = -1
        }

        private fun directive(afterHash: Int) {
            var start = afterHash
            while (start < length && (text[start] == ' ' || text[start] == '\t')) start++
            when {
                isWord(start, "ifdef") || isWord(start, "ifndef") || isWord(start, "if") -> directives.push(line)
                isWord(start, "endif") -> if (directives.size > 0) add(directives.pop(), line)
                isWord(start, "region") -> regionDirectives.push(line)
                isWord(start, "endregion") -> if (regionDirectives.size > 0) add(regionDirectives.pop(), line)
            }
        }

        private fun isWord(start: Int, word: String): Boolean {
            if (!text.startsWith(word, start)) return false
            val after = start + word.length
            return after == length || !isIdentifierPart(text[after])
        }

        private fun endOfLine(index: Int): Int {
            val end = text.indexOf('\n', index)
            return if (end < 0) length else end
        }

        /** @return the position after the comment whose content starts at [contentStart] */
        private fun blockComment(contentStart: Int): Int {
            val start = blockCommentStart!!
            val startLine = line
            var depth = 1
            var index = contentStart
            while (index < length) {
                val character = text[index]
                when {
                    character == '\n' -> {
                        newLineInside(index)
                        index++
                    }

                    text.startsWith(blockCommentEnd, index) -> {
                        index += blockCommentEnd.length
                        if (--depth == 0) {
                            if (startLine < line) pending(startLine, index - 1)
                            return index
                        }
                    }

                    nestedBlockComments && text.startsWith(start, index) -> {
                        depth++
                        index += start.length
                    }

                    else -> index++
                }
            }
            return index
        }

        private fun newLineInside(index: Int) {
            endLine()
            line++
            lineStart = index + 1
            // a line that does not start in code ends a run of imports
            closeImports()
        }

        /** @return the position after the string that starts at [index], -1 if [syntax] does not start there */
        private fun string(syntax: BraceStringSyntax, index: Int): Int = when (syntax) {
            is BraceStringSyntax.Quoted -> quoted(syntax, index)
            is BraceStringSyntax.QuoteRun -> quoteRun(syntax, index)
            is BraceStringSyntax.Hashed -> hashed(syntax, index)
            BraceStringSyntax.CppRaw -> cppRaw(index)
        }

        private fun quoted(syntax: BraceStringSyntax.Quoted, index: Int): Int {
            if (!startsWithPrefix(syntax.start, index)) return -1
            var position = index + syntax.start.length
            while (position < length) {
                val character = text[position]
                when {
                    character == '\n' -> {
                        if (!syntax.multiLine) return position
                        newLineInside(position)
                        position++
                    }

                    character == syntax.escape -> position = escaped(position)

                    text.startsWith(syntax.end, position) -> {
                        position += syntax.end.length
                        if (!syntax.doubledEnd || !text.startsWith(syntax.end, position)) return position
                        position += syntax.end.length
                    }

                    else -> position++
                }
            }
            return length
        }

        private fun quoteRun(syntax: BraceStringSyntax.QuoteRun, index: Int): Int {
            if (!startsWithPrefix(syntax.prefix, index)) return -1
            val quotesStart = index + syntax.prefix.length
            val opening = run(quotesStart, syntax.quote)
            if (opening < syntax.minLength) return -1
            val needed = if (syntax.sameLength) opening else syntax.minLength
            var position = quotesStart + opening
            while (position < length) {
                val character = text[position]
                when (character) {
                    '\n' -> {
                        newLineInside(position)
                        position++
                    }

                    syntax.escape -> position = escaped(position)
                    syntax.quote -> {
                        val closing = run(position, syntax.quote)
                        position += closing
                        if (closing >= needed) return position
                    }

                    else -> position++
                }
            }
            return length
        }

        /** @return the position after the escape at [index] and the character it escapes, which may end a line */
        private fun escaped(index: Int): Int {
            if (index + 1 < length && text[index + 1] == '\n') newLineInside(index + 1)
            return index + 2
        }

        private fun hashed(syntax: BraceStringSyntax.Hashed, index: Int): Int {
            if (!startsWithPrefix(syntax.prefix, index)) return -1
            val hashesStart = index + syntax.prefix.length
            val hashes = run(hashesStart, '#')
            if (hashes < syntax.minHashes || !text.startsWith(syntax.quote, hashesStart + hashes)) return -1
            var position = hashesStart + hashes + syntax.quote.length
            while (position < length) {
                val character = text[position]
                if (character == '\n') {
                    if (!syntax.multiLine) return position
                    newLineInside(position)
                    position++
                } else if (
                    text.startsWith(syntax.quote, position) && run(position + syntax.quote.length, '#') >= hashes
                ) {
                    return position + syntax.quote.length + hashes
                } else {
                    position++
                }
            }
            return length
        }

        private fun cppRaw(index: Int): Int {
            if (!text.startsWith("R\"", index) || !hasCppRawPrefix(index)) return -1
            val delimiterStart = index + 2
            var parenthesis = delimiterStart
            while (parenthesis < length && parenthesis - delimiterStart <= MAX_CPP_DELIMITER) {
                val character = text[parenthesis]
                if (character == '(') break
                if (character == ')' || character == '\\' || character == '"' || character.isWhitespace()) return -1
                parenthesis++
            }
            if (parenthesis >= length || text[parenthesis] != '(') return -1
            val delimiterLength = parenthesis - delimiterStart
            var position = parenthesis + 1
            while (position < length) {
                val character = text[position]
                if (character == '\n') {
                    newLineInside(position)
                } else if (
                    character == ')' &&
                    text.regionMatches(position + 1, text, delimiterStart, delimiterLength) &&
                    position + 1 + delimiterLength < length && text[position + 1 + delimiterLength] == '"'
                ) {
                    return position + delimiterLength + 2
                }
                position++
            }
            return length
        }

        private fun hasCppRawPrefix(index: Int): Boolean {
            var wordStart = index
            while (wordStart > lineStart && isIdentifierPart(text[wordStart - 1])) wordStart--
            if (wordStart == index) return true
            val prefixLength = index - wordStart
            return CPP_RAW_PREFIXES.any { it.length == prefixLength && text.startsWith(it, wordStart) }
        }

        /** a prefix that starts with a letter must not continue a word */
        private fun startsWithPrefix(prefix: String, index: Int): Boolean {
            if (!text.startsWith(prefix, index)) return false
            return prefix.isEmpty() || !prefix[0].isLetter() || index == 0 || !isIdentifierPart(text[index - 1])
        }

        /** @return the position after a character like `'a'`, the next position if this is none */
        private fun character(index: Int): Int {
            val content = index + 1
            if (content >= length) return content
            val first = text[content]
            if (first == '\\') {
                var position = content + 2
                while (position < length && position - content <= MAX_ESCAPE && text[position] != '\n') {
                    if (text[position] == '\'') return position + 1
                    position++
                }
                return content
            }
            if (first == '\n' || first == '\'') return content
            val after = content + if (first.isHighSurrogate()) 2 else 1
            return if (after < length && text[after] == '\'') after + 1 else content
        }

        private fun run(start: Int, character: Char): Int {
            var position = start
            while (position < length && text[position] == character) position++
            return position - start
        }

        private fun add(startLine: Int, endLine: Int) {
            if (endLine <= startLine) return
            if (startLine >= bestEnd.size) {
                val old = bestEnd
                bestEnd = IntArray(maxOf(old.size * 2, startLine + 1)) { if (it < old.size) old[it] else -1 }
            }
            if (endLine > bestEnd[startLine]) bestEnd[startLine] = endLine
        }

        private fun regions(): List<FoldRegion> {
            val regions = ArrayList<FoldRegion>()
            for (start in bestEnd.indices) {
                if (bestEnd[start] >= 0) regions += FoldRegion(start, bestEnd[start])
            }
            return regions
        }
    }

    /** a growable stack of ints without boxing */
    private class IntStack {
        private var values = IntArray(INITIAL_CAPACITY)
        var size = 0
            private set

        fun push(value: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun pop(): Int = values[--size]

        operator fun get(index: Int): Int = values[index]

        fun clear() {
            size = 0
        }
    }

    private companion object {
        const val OPENING = "{[("
        const val CLOSING = "}])"
        const val BRACKET_KINDS = 3
        const val KIND_MASK = 3
        const val ASCII = 128
        const val INITIAL_CAPACITY = 16
        const val MAX_CPP_DELIMITER = 16
        const val MAX_ESCAPE = 12
        val CPP_RAW_PREFIXES = listOf("L", "u", "U", "u8")

        fun isIdentifierPart(character: Char) = character.isLetterOrDigit() || character == '_'
    }
}

/**
 * Folds Java: blocks, arrays, multi-line parentheses, comments including Javadoc and runs of imports. Text blocks
 * `"""` are strings.
 */
object JavaFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    strings = listOf(BraceStringSyntax.QuoteRun(escape = '\\'), BraceStringSyntax.Quoted("\"")),
    importPrefixes = listOf("import "),
)

/**
 * Folds Kotlin: blocks and lambdas, lists, multi-line parentheses, nested comments including KDoc and runs of imports.
 * Raw strings `"""` and names in backticks are strings.
 */
object KotlinFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    nestedBlockComments = true,
    strings = listOf(
        BraceStringSyntax.QuoteRun(),
        BraceStringSyntax.Quoted("\""),
        BraceStringSyntax.Quoted("`", escape = null),
    ),
    importPrefixes = listOf("import "),
)

/** Folds JSON: objects and arrays. Comments of JSONC are understood too. */
object JsonFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    charLiterals = false,
    foldedBrackets = "{}[]",
)

/** Folds C: blocks, multi-line parentheses, comments, runs of `#include` and `#if … #endif`. */
object CFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    importPrefixes = listOf("#include"),
    preprocessorFolds = true,
)

/**
 * Folds C++: blocks, multi-line parentheses, comments, runs of `#include`, `import` and `using`, and `#if … #endif`.
 * Raw strings `R"x(…)x"` are strings, digit separators like `1'000` are no characters.
 */
object CppFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    strings = listOf(BraceStringSyntax.CppRaw, BraceStringSyntax.Quoted("\"")),
    importPrefixes = listOf("#include", "import ", "using "),
    preprocessorFolds = true,
)

/**
 * Folds C#: blocks, multi-line parentheses, comments, runs of `using`, `#if … #endif` and `#region … #endregion`.
 * Verbatim strings `@"…"` and raw strings `"""…"""` are strings.
 */
object CSharpFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    strings = listOf(
        BraceStringSyntax.QuoteRun(sameLength = true),
        BraceStringSyntax.Quoted("@\"", "\"", escape = null, multiLine = true, doubledEnd = true),
        BraceStringSyntax.Quoted("@$\"", "\"", escape = null, multiLine = true, doubledEnd = true),
        BraceStringSyntax.Quoted("$@\"", "\"", escape = null, multiLine = true, doubledEnd = true),
        BraceStringSyntax.Quoted("\""),
    ),
    importPrefixes = listOf("using ", "global using "),
    preprocessorFolds = true,
)

/**
 * Folds Go: blocks, multi-line parentheses like `import ( … )`, `const ( … )` and calls, comments and runs of single
 * imports. Raw strings in backticks are strings.
 */
object GoFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    strings = listOf(BraceStringSyntax.Quoted("\""), BraceStringSyntax.Quoted("`", escape = null, multiLine = true)),
    importPrefixes = listOf("import "),
)

/**
 * Folds Rust: blocks, arrays, multi-line parentheses, nested comments and runs of `use` and `extern crate`. Raw
 * strings `r#"…"#` are strings, lifetimes like `'a` are no characters.
 */
object RustFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    nestedBlockComments = true,
    strings = listOf(
        BraceStringSyntax.Hashed("r"),
        BraceStringSyntax.Hashed("br"),
        BraceStringSyntax.Hashed("cr"),
        BraceStringSyntax.Quoted("\"", multiLine = true),
    ),
    importPrefixes = listOf("use ", "pub use ", "extern crate "),
)

/**
 * Folds Swift: blocks, arrays, multi-line parentheses, nested comments, runs of imports and `#if … #endif`. Multi-line
 * strings `"""` and strings with `#` like `#"…"#` are strings.
 */
object SwiftFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    nestedBlockComments = true,
    strings = listOf(
        BraceStringSyntax.Hashed("", "\"\"\"", minHashes = 1),
        BraceStringSyntax.Hashed("", "\"", minHashes = 1, multiLine = false),
        BraceStringSyntax.QuoteRun(escape = '\\'),
        BraceStringSyntax.Quoted("\""),
        BraceStringSyntax.Quoted("`", escape = null),
    ),
    charLiterals = false,
    importPrefixes = listOf("import ", "@testable import "),
    preprocessorFolds = true,
)

/**
 * Folds Dart: blocks, lists, multi-line parentheses like trees of Flutter widgets, nested comments and runs of
 * `import`, `export` and `part`. Strings with `'`, `"`, `'''`, `"""` and raw strings like `r'…'` are strings.
 */
object DartFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    nestedBlockComments = true,
    strings = listOf(
        BraceStringSyntax.QuoteRun('"', prefix = "r"),
        BraceStringSyntax.QuoteRun('\'', prefix = "r"),
        BraceStringSyntax.Quoted("r\"", "\"", escape = null),
        BraceStringSyntax.Quoted("r'", "'", escape = null),
        BraceStringSyntax.QuoteRun('"', escape = '\\'),
        BraceStringSyntax.QuoteRun('\'', escape = '\\'),
        BraceStringSyntax.Quoted("\""),
        BraceStringSyntax.Quoted("'"),
    ),
    charLiterals = false,
    importPrefixes = listOf("import ", "export ", "part "),
)

/**
 * Folds JavaScript: blocks, objects, arrays, multi-line parentheses, comments including JSDoc and runs of imports.
 * Strings with `'` and `"`, and template strings in backticks are strings. Brackets in regex literals like `/\{/`
 * count as code.
 */
object JavaScriptFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    strings = listOf(
        BraceStringSyntax.Quoted("\""),
        BraceStringSyntax.Quoted("'"),
        BraceStringSyntax.Quoted("`", multiLine = true),
    ),
    charLiterals = false,
    importPrefixes = listOf("import "),
)

/** Folds CSS: rules, at-rules like `@media`, multi-line parentheses, comments and runs of `@import`. */
object CssFoldProvider : KodeEditorFoldProvider by BraceFoldProvider(
    lineComments = emptyList(),
    strings = listOf(BraceStringSyntax.Quoted("\""), BraceStringSyntax.Quoted("'")),
    charLiterals = false,
    foldedBrackets = "{}()",
    importPrefixes = listOf("@import "),
)
