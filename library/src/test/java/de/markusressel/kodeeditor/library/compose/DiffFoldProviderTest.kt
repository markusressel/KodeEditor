package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class DiffFoldProviderTest {

    private fun regions(vararg lines: String) =
        DiffFoldProvider.foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }
            .map { it.startLine to it.endLine }

    @Test
    fun filesAndHunksOfAGitDiffFold() {
        assertEquals(
            listOf(0 to 10, 4 to 7, 8 to 10, 11 to 17, 15 to 17),
            regions(
                "diff --git a/a.txt b/a.txt",
                "index 1234567..89abcde 100644",
                "--- a/a.txt",
                "+++ b/a.txt",
                "@@ -1,2 +1,2 @@",
                " one",
                "-two",
                "+zwei",
                "@@ -10,2 +10,1 @@ fun x()",
                " ten",
                "-eleven",
                "diff --git a/b.txt b/b.txt",
                "new file mode 100644",
                "--- /dev/null",
                "+++ b/b.txt",
                "@@ -0,0 +1,2 @@",
                "+a",
                "+b",
            ),
        )
    }

    @Test
    fun aDiffWithoutGitStartsFilesWithTheirNames() {
        assertEquals(
            listOf(0 to 4, 2 to 4, 5 to 8, 7 to 8),
            regions(
                "--- a/x",
                "+++ b/x",
                "@@ -1 +1 @@",
                "-a",
                "+b",
                "--- a/y",
                "+++ b/y",
                "@@ -1 +1,0 @@",
                "-c",
            ),
        )
    }

    @Test
    fun removedLinesThatStartWithTwoDashesAreNoFile() {
        assertEquals(
            listOf(0 to 6, 2 to 6),
            regions(
                "--- a/q.sql",
                "+++ b/q.sql",
                "@@ -1,2 +1,2 @@",
                "--- old comment",
                "+++ new comment",
                " select 1;",
                "\\ No newline at end of file",
            ),
        )
    }

    @Test
    fun whatFollowsThePatchIsNotPartOfIt() {
        assertEquals(
            listOf(3 to 8, 6 to 8),
            regions(
                "From 1234 Mon Sep 17 00:00:00 2001",
                "Subject: [PATCH] fix",
                "---",
                "diff --git a/a b/a",
                "--- a/a",
                "+++ b/a",
                "@@ -1 +1 @@",
                "-x",
                "+y",
                "-- ",
                "2.42.0",
            ),
        )
    }

    @Test
    fun aHunkWithWrongCountsEndsAtTheNextHunk() {
        assertEquals(
            listOf(0 to 2, 3 to 4),
            regions("@@ -1,9 +1,9 @@", " a", "-b", "@@ -20,1 +20,1 @@", "+c", "", ""),
        )
    }

    @Test
    fun aHunkWithoutCountsEndsAtTheNextFile() {
        assertEquals(
            listOf(1 to 3, 4 to 6),
            regions("--- a", "@@ @@", "-x", "--- y", "--- b", "+++ c", "text"),
        )
    }

    @Test
    fun blankLinesAtTheEndStayVisible() {
        assertEquals(listOf(0 to 1), regions("@@ @@", "-a", "", "", "diff x"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions(""))
    }

    @Test
    fun brokenTextDoesNotCrash() {
        val sample = listOf(
            "diff --git a/a b/a", "--- a/a", "+++ b/a", "@@ -1,2 +1,99999999999 @@", " a", "-b", "+c",
            "\\ No newline at end of file", "@@ -x +y @@", "diff",
        ).joinToString("\n")
        assertValidForAllPrefixes(sample) { DiffFoldProvider.foldRegions(it) }
        assertValidForRandomText("-+@ ,1\\d\n") { DiffFoldProvider.foldRegions(it) }
    }
}
