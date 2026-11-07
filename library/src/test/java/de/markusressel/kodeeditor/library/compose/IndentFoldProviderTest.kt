package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class IndentFoldProviderTest {

    private fun KodeEditorFoldProvider.regions(vararg lines: String) = regionsOf(lines.joinToString("\n"))

    private fun KodeEditorFoldProvider.regionsOf(text: String) =
        foldRegions(text).sortedBy { it.startLine }.map { it.startLine to it.endLine }

    private val none = emptyList<Pair<Int, Int>>()

    private val indent = IndentFoldProvider()

    // indentation

    @Test
    fun aLineFoldsTheDeeperLinesBelowIt() {
        assertEquals(listOf(0 to 2), indent.regions("a", "  b", "  c", "d"))
    }

    @Test
    fun blocksNest() {
        assertEquals(listOf(0 to 3, 1 to 2), indent.regions("a", "  b", "    c", "  d", "e"))
    }

    @Test
    fun blankLinesWithinABlockDontEndItAndThoseAtItsEndStayVisible() {
        assertEquals(listOf(0 to 3), indent.regions("a", "  b", "", "  c", "", "", "   ", "d"))
    }

    @Test
    fun aCommentDoesNotEndABlock() {
        assertEquals(listOf(0 to 3), indent.regions("a", "  b", "# c", "  d", "e"))
    }

    @Test
    fun aCommentAtTheEndBelongsToTheBlockWhenItIsDeeper() {
        assertEquals(listOf(0 to 1), indent.regions("a", "  b", "# c", "d"))
        assertEquals(listOf(0 to 2), indent.regions("a", "  b", "  # c", "d"))
    }

    @Test
    fun aTabGoesToTheNextTabStop() {
        assertEquals(listOf(0 to 2), indent.regions("a", "\tb", "    c", "d"))
        assertEquals(listOf(0 to 2, 1 to 2), IndentFoldProvider(tabWidth = 2).regions("a", "\tb", "    c"))
        assertEquals(listOf(0 to 2), indent.regions("a", "  \tb", "    c"))
    }

    @Test
    fun theCommentPrefixesCanBeChosen() {
        val provider = IndentFoldProvider(lineCommentPrefixes = listOf("//", "--"))
        assertEquals(listOf(0 to 4), provider.regions("a", "  b", "// c", "-- d", "  e"))
        assertEquals(listOf(0 to 1, 2 to 3), indent.regions("a", "  b", "// c", "  d"))
    }

    @Test
    fun aLineWithoutDeeperLinesHasNoRegion() {
        assertEquals(none, indent.regions("a", "b", "  ", "c"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        assertEquals(none, indent.regionsOf(""))
        assertEquals(none, indent.regionsOf("\n\n"))
        assertEquals(none, PythonFoldProvider.regionsOf(""))
        assertEquals(none, YamlFoldProvider.regionsOf(""))
    }

    @Test
    fun aTrailingLineBreakAndCarriageReturnsDontMatter() {
        assertEquals(listOf(0 to 1), indent.regionsOf("a\n  b"))
        assertEquals(listOf(0 to 1), indent.regionsOf("a\n  b\n"))
        assertEquals(listOf(0 to 1), indent.regionsOf("a\r\n  b\r\n\r\nc"))
        assertEquals(listOf(0 to 1), PythonFoldProvider.regionsOf("if a:\r\n    b\r\n"))
    }

    @Test
    fun deepBlocksAndManyBracketsFold() {
        val depth = 40
        val lines = Array(depth) { " ".repeat(it) + "x" }
        assertEquals((0 until depth - 1).map { it to depth - 1 }, indent.regions(*lines))
        val brackets = "(".repeat(depth) + "\n" + ")".repeat(depth)
        assertEquals(none, PythonFoldProvider.regionsOf(brackets))
        assertEquals(listOf(0 to 1), PythonFoldProvider.regionsOf(brackets.replace("\n", "\n1")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun theTabWidthIsPositive() {
        IndentFoldProvider(tabWidth = 0)
    }

    // python

    @Test
    fun pythonFoldsBlocks() {
        assertEquals(
            listOf(0 to 1, 4 to 12, 7 to 8, 10 to 12, 15 to 16),
            PythonFoldProvider.regions(
                "import os",
                "import sys",
                "",
                "",
                "class Greeter:",
                "    \"\"\"Greets people.\"\"\"",
                "",
                "    def __init__(self, name):",
                "        self.name = name",
                "",
                "    def greet(self):",
                "        # say hello",
                "        print(f\"Hello {self.name}\")",
                "",
                "",
                "if __name__ == \"__main__\":",
                "    Greeter(\"x\").greet()",
            ),
        )
    }

    @Test
    fun aPythonDocstringFoldsAndIsNoCode() {
        assertEquals(
            listOf(0 to 6, 1 to 5),
            PythonFoldProvider.regions(
                "def f():",
                "    \"\"\"",
                "    Summary.",
                "",
                "not code: (it is in the string",
                "    \"\"\"",
                "    return 1",
                "x = 2",
            ),
        )
    }

    @Test
    fun aPythonStringWithAPrefixFolds() {
        assertEquals(listOf(0 to 2), PythonFoldProvider.regions("x = rb'''", "a", "'''", "y = 1"))
        assertEquals(listOf(0 to 2), PythonFoldProvider.regions("x = f\"\"\"{a}", "  \" '' \"", "\"\"\"", "y = 1"))
    }

    @Test
    fun aPythonStringInOneLineHasNoRegion() {
        assertEquals(none, PythonFoldProvider.regions("\"\"\"doc\"\"\"", "x = ''", "y = '''a''' + \"\"\"b\"\"\""))
    }

    @Test
    fun anOpenPythonStringReachesTheEnd() {
        assertEquals(listOf(0 to 1), PythonFoldProvider.regionsOf("\"\"\"\nabc\n"))
    }

    @Test
    fun pythonBracketsFoldAndTheirLinesAreNoBlocks() {
        assertEquals(
            listOf(0 to 2),
            PythonFoldProvider.regions("result = call(", "    first,", "        second,", ")", "z = 1"),
        )
    }

    @Test
    fun aClosingBracketInTheMiddleOfALineIsHidden() {
        assertEquals(listOf(0 to 1), PythonFoldProvider.regions("x = (1,", "     2)", "y"))
        assertEquals(listOf(0 to 1), PythonFoldProvider.regions("x = foo([", "    1,", "])", "y"))
    }

    @Test
    fun aBlockAfterBracketsFoldsWithThem() {
        assertEquals(
            listOf(0 to 4),
            PythonFoldProvider.regions("def f(", "    a,", "    b,", ") -> int:", "    return a", "x"),
        )
    }

    @Test
    fun anOpenPythonBracketReachesTheEnd() {
        assertEquals(listOf(0 to 2), PythonFoldProvider.regions("x = (", "  1,", "  2", ""))
    }

    @Test
    fun aBackslashContinuesThePythonLine() {
        assertEquals(listOf(0 to 2), PythonFoldProvider.regions("if a and \\", "        b:", "    pass", "c"))
        assertEquals(none, PythonFoldProvider.regions("x = 1 + \\", "    2", "y"))
    }

    @Test
    fun pythonStringsAndCommentsDontOpenBrackets() {
        assertEquals(
            listOf(2 to 3, 5 to 6),
            PythonFoldProvider.regions(
                "x = \"# not ( a comment\"",
                "y = ')'  # (",
                "def f():",
                "    pass",
                "s = \"a \\\" ( b\"",
                "def g():",
                "    pass",
            ),
        )
    }

    @Test
    fun pythonImportsInARowFold() {
        assertEquals(
            listOf(0 to 7, 1 to 3),
            PythonFoldProvider.regions(
                "import os",
                "from typing import (",
                "    List,",
                "    Dict,",
                ")",
                "# comment",
                "",
                "import re",
                "",
                "x = 1",
            ),
        )
    }

    @Test
    fun aSinglePythonImportHasNoRegion() {
        assertEquals(none, PythonFoldProvider.regions("import os", "", "x = 1", "important = 2", "from_ = 3"))
    }

    @Test
    fun aPythonTabIsEightColumns() {
        assertEquals(listOf(0 to 2), PythonFoldProvider.regions("if a:", "\tb", "        c", "d"))
    }

    @Test
    fun pythonBlankLinesAndCommentsAtTheEndOfABlock() {
        assertEquals(
            listOf(0 to 2),
            PythonFoldProvider.regions("def f():", "    a", "    # done", "", "# about g", "def g(): pass"),
        )
    }

    // yaml

    @Test
    fun yamlFoldsMappingsAndSequences() {
        assertEquals(
            listOf(1 to 5, 3 to 5, 7 to 8),
            YamlFoldProvider.regions(
                "# config",
                "server:",
                "  host: localhost",
                "  ports:",
                "    - 80",
                "    - 443",
                "",
                "database:",
                "  user: admin",
            ),
        )
    }

    @Test
    fun aYamlSequenceCanBeIndentedLikeItsKey() {
        assertEquals(
            listOf(0 to 4, 1 to 2, 3 to 4),
            YamlFoldProvider.regions(
                "steps:",
                "- name: build",
                "  run: make",
                "- name: test",
                "  run: make test",
                "other: 1",
            ),
        )
    }

    @Test
    fun theContentOfAYamlItemIsDeeperThanItsDash() {
        assertEquals(
            listOf(0 to 4, 1 to 3, 2 to 3),
            YamlFoldProvider.regions("jobs:", "  - name: a", "    env:", "      X: 1", "  - name: b"),
        )
        assertEquals(listOf(0 to 2), YamlFoldProvider.regions("- - a", "    b", "  - c", "- d"))
    }

    @Test
    fun aYamlCommentDoesNotEndAMapping() {
        assertEquals(listOf(0 to 3), YamlFoldProvider.regions("a:", "  b: 1", "# note", "  c: 2", "d: 3"))
    }

    @Test
    fun aYamlBlockScalarFoldsAndIsText() {
        assertEquals(
            listOf(0 to 5),
            YamlFoldProvider.regions(
                "script: |",
                "  echo \"a\"",
                "  - not a list",
                "    key: not a key",
                "",
                "  # not a comment",
                "",
                "next: 1",
            ),
        )
    }

    @Test
    fun yamlBlockScalarsHaveIndicators() {
        assertEquals(
            listOf(0 to 2, 3 to 5, 6 to 8, 7 to 8),
            YamlFoldProvider.regions(
                "a: >-",
                "  - x",
                "    y",
                "b: |+2 # comment",
                "   - z",
                "     w",
                "c: |x",
                "  - v",
                "    u",
            ),
        )
    }

    @Test
    fun aYamlBlockScalarEndsAtALineThatIsNotDeeper() {
        assertEquals(
            listOf(0 to 4, 1 to 2, 3 to 4),
            YamlFoldProvider.regions("a:", "  b: |", "    - x", "  c:", "    d: 1"),
        )
        assertEquals(
            listOf(0 to 2, 4 to 6),
            YamlFoldProvider.regions("- |", "  - a", "    b", "- c", "- >", "  d", "  e"),
        )
    }

    @Test
    fun aQuotedYamlKeyCanHaveAHashAndAColon() {
        assertEquals(
            listOf(0 to 2, 3 to 5),
            YamlFoldProvider.regions(
                "\"a # b\": |",
                "  - z",
                "    w",
                "'it''s: # c': >",
                "  - q",
                "    r",
            ),
        )
    }

    @Test
    fun aYamlHashAfterTextIsNoComment() {
        assertEquals(listOf(0 to 2), YamlFoldProvider.regions("a#b: |", "  - c", "    d"))
    }

    @Test
    fun eachYamlDocumentFolds() {
        assertEquals(
            listOf(1 to 3, 2 to 3, 5 to 6),
            YamlFoldProvider.regions("a: 1", "---", "b:", "  c: 1", "", "--- # second", "d: 2", "...", ""),
        )
    }

    @Test
    fun aYamlDocumentWithoutContentHasNoRegion() {
        assertEquals(listOf(2 to 5), YamlFoldProvider.regions("---", "", "---", "a: 1", "----", "b"))
    }
}
