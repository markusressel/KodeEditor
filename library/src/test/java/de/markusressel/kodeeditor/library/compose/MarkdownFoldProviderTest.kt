package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFoldProviderTest {

    private fun regions(vararg lines: String) =
        MarkdownFoldProvider.foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }
            .map { it.startLine to it.endLine }

    @Test
    fun aSectionEndsAtTheNextHeadingOfTheSameLevel() {
        assertEquals(listOf(0 to 1, 2 to 3), regions("# a", "1", "# b", "2"))
    }

    @Test
    fun aSectionContainsDeeperHeadings() {
        assertEquals(
            listOf(0 to 4, 1 to 2, 3 to 4, 5 to 6),
            regions("# a", "## b", "1", "## c", "2", "# d", "3"),
        )
    }

    @Test
    fun blankLinesAtTheEndStayVisible() {
        assertEquals(listOf(0 to 1), regions("# a", "1", "", "   ", "# b"))
    }

    @Test
    fun aHeadingWithoutContentHasNoRegion() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("# a", "", "# b", ""))
    }

    @Test
    fun aHashWithoutASpaceIsNoHeading() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("#a", "1", "####### b", "2"))
    }

    @Test
    fun aDeepIndentIsNoHeading() {
        assertEquals(listOf(0 to 1), regions("   # a", "    # b"))
    }

    @Test
    fun anEmptyHeadingIsAHeading() {
        assertEquals(listOf(0 to 1), regions("#", "1"))
    }

    @Test
    fun fencedCodeHasNoHeadings() {
        assertEquals(
            listOf(0 to 3),
            regions("# a", "```", "# comment", "```"),
        )
    }

    @Test
    fun aFenceOnlyEndsWithItsOwnKind() {
        assertEquals(
            listOf(0 to 4),
            regions("# a", "~~~~", "```", "~~~", "# comment"),
        )
    }

    @Test
    fun anOpenFenceReachesTheEnd() {
        assertEquals(listOf(0 to 2), regions("# a", "```", "# comment"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions(""))
    }
}
