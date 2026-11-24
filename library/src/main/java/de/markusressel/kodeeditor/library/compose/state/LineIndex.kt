package de.markusressel.kodeeditor.library.compose.state


/**
 * Where the lines of the text start. A change only touches the lines it is in, and moves the ones after it.
 */
internal class LineIndex(text: String) {
    private var starts = IntArray(16)

    var count = 0
        private set

    init {
        add(0)
        var index = text.indexOf('\n')
        while (index >= 0) {
            add(index + 1)
            index = text.indexOf('\n', index + 1)
        }
    }

    private fun add(start: Int) {
        if (count == starts.size) starts = starts.copyOf(count * 2)
        starts[count++] = start
    }

    fun start(line: Int): Int = starts[line.coerceIn(0, count - 1)]

    /** @return where the lines start now, a copy */
    fun starts(): IntArray = starts.copyOf(count)

    fun lineOf(offset: Int): Int {
        var low = 0
        var high = count - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (starts[middle] <= offset) low = middle else high = middle - 1
        }
        return low
    }

    fun longest(textLength: Int): Int {
        var best = 0
        var bestLength = -1
        for (line in 0 until count) {
            val end = if (line + 1 < count) starts[line + 1] - 1 else textLength
            val length = end - starts[line]
            if (length > bestLength) {
                bestLength = length
                best = line
            }
        }
        return best
    }

    /**
     * @param old the text before the change
     * @param splice the change
     */
    fun apply(old: String, splice: TextSplice) {
        val delta = splice.inserted.length - splice.removedLength
        val removedEnd = splice.start + splice.removedLength
        val firstLine = lineOf(splice.start)
        var removedLines = 0
        for (index in splice.start until removedEnd) if (old[index] == '\n') removedLines++
        var insertedLines = 0
        for (char in splice.inserted) if (char == '\n') insertedLines++

        val newCount = count - removedLines + insertedLines
        val result = if (newCount > starts.size) IntArray(newCount * 2) else starts
        // the part that moves, handled first so it is not overwritten when the array is reused
        val tailFrom = firstLine + 1 + removedLines
        val tailTo = firstLine + 1 + insertedLines
        System.arraycopy(starts, tailFrom, result, tailTo, count - tailFrom)
        if (result !== starts) System.arraycopy(starts, 0, result, 0, firstLine + 1)
        for (line in tailTo until newCount) result[line] += delta
        var line = firstLine + 1
        splice.inserted.forEachIndexed { index, char ->
            if (char == '\n') result[line++] = splice.start + index + 1
        }
        starts = result
        count = newCount
    }
}
