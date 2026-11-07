package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class PropertiesFoldProviderTest {

    private fun regions(vararg lines: String) =
        PropertiesFoldProvider.foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }
            .map { it.startLine to it.endLine }

    @Test
    fun aContinuedValueFolds() {
        assertEquals(
            listOf(0 to 2),
            regions("fruits = apple, \\", "         banana, \\", "         pear", "a = b"),
        )
    }

    @Test
    fun anEvenCountOfBackslashesEndsTheValue() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("path = C:\\\\", "b = c"))
    }

    @Test
    fun aContinuationLineThatLooksLikeACommentIsPartOfTheValue() {
        assertEquals(listOf(0 to 2), regions("a = 1 \\", "# 2 \\", "3", "# x"))
    }

    @Test
    fun windowsLineEndsAreIgnored() {
        assertEquals(listOf(0 to 1), regions("a = 1 \\\r", "  2\r", "b = 3\r"))
    }

    @Test
    fun aValueContinuedToTheEndFolds() {
        assertEquals(listOf(0 to 1), regions("a = 1 \\", "2 \\"))
    }

    @Test
    fun commentLinesInARowFold() {
        assertEquals(
            listOf(0 to 2),
            regions("# Licensed under", "! the Apache License", "# 2.0", "", "a = b"),
        )
    }

    @Test
    fun aSingleCommentLineHasNoRegion() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("# a", "", "# b", "c = d"))
    }

    @Test
    fun aGroupStartsWithACommentAndHasTwoEntries() {
        assertEquals(
            listOf(0 to 3, 1 to 2, 4 to 5, 7 to 9),
            regions(
                "# database",
                "db.url = jdbc:x \\",
                "    ;y",
                "db.user = sa",
                "# cache",
                "# in minutes",
                "cache.ttl = 5",
                "# mail",
                "mail.host = a",
                "mail.port = 25",
                "",
                "other = 1",
                "more = 2",
            ),
        )
    }

    @Test
    fun aGroupEndsWithABlankLine() {
        assertEquals(listOf(0 to 2), regions("# a", "b = 1", "c = 2", "", "d = 3"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions(""))
    }

    @Test
    fun aTrailingNewlineDoesNotMatter() {
        assertEquals(listOf(0 to 1), regions("a = 1 \\", "2", ""))
    }

    @Test
    fun brokenTextDoesNotCrash() {
        val sample = listOf("# a", "! b", "c = d \\", "  e", "f:g", "", "h \\\\", "i = \\").joinToString("\n")
        assertValidForAllPrefixes(sample) { PropertiesFoldProvider.foldRegions(it) }
        assertValidForRandomText("#!\\ a=\r\n") { PropertiesFoldProvider.foldRegions(it) }
    }
}
