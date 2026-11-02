package de.markusressel.kodeeditor.demo

/**
 * One edit of a text, to find out where its lines went. Anything that points to a line, like a bookmark, has to follow
 * the text of that line, because the bar only knows line numbers.
 *
 * The edit is the part of the text between the common start and the common end of [old] and [new].
 *
 * @param old the text before the edit
 * @param new the text after the edit
 */
class LineEdit(private val old: String, private val new: String) {
    private val start = old.commonPrefixWith(new).length
    private val suffix = old.substring(start).commonSuffixWith(new.substring(start)).length
    private val oldEnd = old.length - suffix
    private val newEnd = new.length - suffix

    /** Without a line break going or coming, every line stays where it is. */
    private val movesLines = old.indexOf('\n', start).let { it in 0 until oldEnd } ||
        new.indexOf('\n', start).let { it in 0 until newEnd }

    /**
     * @param line a line of the old text
     * @return where the text of that line is now, a line that lost part of its text included, null if nothing of it
     * is left
     */
    fun lineAfterEdit(line: Int): Int? {
        if (!movesLines) return line
        val lineStart = offsetOfLine(line) ?: return null
        val lineEnd = old.indexOf('\n', lineStart).let { if (it < 0) old.length else it }
        val isEmptyLineAtEditEnd = lineStart == lineEnd && lineStart == oldEnd
        return when {
            lineStart < start -> line
            lineEnd > oldEnd || isEmptyLineAtEditEnd -> {
                val newOffset = newEnd + maxOf(lineStart - oldEnd, 0)
                new.substring(0, newOffset).count { it == '\n' }
            }

            else -> null
        }
    }

    private fun offsetOfLine(line: Int): Int? {
        var offset = 0
        repeat(line) {
            val lineBreak = old.indexOf('\n', offset)
            if (lineBreak < 0) return null
            offset = lineBreak + 1
        }
        return offset
    }
}
