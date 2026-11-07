package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class TomlFoldProviderTest {

    private fun regions(vararg lines: String) =
        TomlFoldProvider.foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }
            .map { it.startLine to it.endLine }

    @Test
    fun aTableFoldsUntilTheNextHeader() {
        assertEquals(
            listOf(1 to 3, 5 to 6, 7 to 8, 9 to 10),
            regions(
                "title = \"example\"",
                "[owner]",
                "name = \"Tom\"",
                "dob = 1979-05-27",
                "",
                "[[products]]",
                "name = \"Hammer\"",
                "[[products]]",
                "name = \"Nail\"",
                "  [servers.alpha]",
                "  ip = \"10.0.0.1\"",
            ),
        )
    }

    @Test
    fun blankLinesAtTheEndOfATableStayVisible() {
        assertEquals(listOf(0 to 1), regions("[a]", "b = 1", "", "  ", "[c]"))
    }

    @Test
    fun commentsAtTheEndOfATableAreHidden() {
        assertEquals(listOf(0 to 2), regions("[a]", "b = 1", "# about c", "[c]"))
    }

    @Test
    fun aTableWithoutContentHasNoRegion() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("[a]", "", "[b]"))
    }

    @Test
    fun arraysOverSeveralLinesFold() {
        assertEquals(
            listOf(0 to 6, 1 to 5, 2 to 4),
            regions(
                "[a]",
                "b = [",
                "  [",
                "    1, 2,",
                "  ],",
                "]",
                "c = 1",
            ),
        )
    }

    @Test
    fun theEndStaysVisibleIfMoreFollowsOnItsLine() {
        assertEquals(listOf(0 to 4, 2 to 3), regions("b = [", "  [1,", "   2], [3,", "   4],", "]"))
    }

    @Test
    fun inlineTablesOverSeveralLinesFold() {
        assertEquals(listOf(0 to 2), regions("a = {", "  b = 1,", "} # end"))
    }

    @Test
    fun aLineInAnArrayThatStartsWithABracketIsNoHeader() {
        assertEquals(listOf(0 to 3), regions("a = [", "[1, 2],", "[\"x\"],", "]"))
    }

    @Test
    fun aHeaderEndsAnArrayThatIsStillOpen() {
        assertEquals(listOf(0 to 1, 2 to 3), regions("[a]", "b = [", "[c]", "d = 1"))
    }

    @Test
    fun multiLineStringsFoldAndAreSkipped() {
        assertEquals(
            listOf(0 to 6, 1 to 4, 5 to 6),
            regions(
                "[a]",
                "b = \"\"\"",
                "[not.a.header]",
                "]",
                "\"\"\"",
                "c = '''[x]",
                "'''''",
                "[d]",
            ),
        )
    }

    @Test
    fun bracketsInStringsAndCommentsAreSkipped() {
        assertEquals(
            listOf(0 to 4),
            regions("a = [", "  \"]\", 'x]',", "  # ]", "  \"\\\"]\",", "]"),
        )
    }

    @Test
    fun aClosingBracketWithoutAnOpeningOneIsIgnored() {
        assertEquals(listOf(0 to 2), regions("[a]", "b = ]", "c = }"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions(""))
    }

    @Test
    fun aTrailingNewlineDoesNotMatter() {
        assertEquals(listOf(0 to 1), regions("[a]", "b = 1", ""))
    }

    @Test
    fun brokenTextDoesNotCrash() {
        val sample = listOf(
            "# c", "[a]", "b = [", "  1, # x", "]", "c = { d = 'e' }", "f = \"\"\"", "g\\", "\"\"\"", "[[h]]",
            "i = '''", "j", "'''",
        ).joinToString("\n")
        assertValidForAllPrefixes(sample) { TomlFoldProvider.foldRegions(it) }
        assertValidForRandomText("[]{}\"'#\\ a=,\n") { TomlFoldProvider.foldRegions(it) }
    }
}
