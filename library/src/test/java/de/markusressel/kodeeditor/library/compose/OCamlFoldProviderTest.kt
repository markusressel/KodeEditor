package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class OCamlFoldProviderTest {

    private fun regions(vararg lines: String) =
        OCamlFoldProvider.foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }
            .map { it.startLine to it.endLine }

    @Test
    fun modulesAndSignaturesFold() {
        assertEquals(
            listOf(0 to 3, 5 to 8),
            regions(
                "module type S = sig",
                "  val f : int ->",
                "    int",
                "end",
                "",
                "module M : S = struct",
                "  let f x =",
                "    x + 1",
                "end",
            ),
        )
    }

    @Test
    fun definitionsFoldUntilTheLastCodeBeforeTheNextOne() {
        assertEquals(
            listOf(0 to 1, 2 to 3, 4 to 5, 8 to 10),
            regions(
                "let rec even n =",
                "  n = 0 || odd (n - 1)",
                "and odd n =",
                "  n <> 0 && even (n - 1)",
                "type t =",
                "  | A",
                "",
                "(** the main function *)",
                "let () =",
                "  print_endline",
                "    \"hello\"",
            ),
        )
    }

    @Test
    fun onlyDefinitionsAtTheStartOfALineFold() {
        assertEquals(
            listOf(0 to 3),
            regions("let f () =", "  let x = 1 in", "  x", "  + 1"),
        )
    }

    @Test
    fun bracketsFold() {
        assertEquals(
            listOf(1 to 4, 5 to 7, 8 to 10),
            regions(
                "",
                "  let l = [",
                "    1;",
                "    2;",
                "  ]",
                "  let a = [|",
                "    1",
                "  |]",
                "  let r = {",
                "    x = 1",
                "  }",
            ),
        )
    }

    @Test
    fun theEndStaysVisibleIfMoreFollowsOnItsLine() {
        assertEquals(listOf(1 to 2), regions("", "  begin", "    f ()", "  end in", "  g ()"))
    }

    @Test
    fun commentsNestAndFold() {
        assertEquals(
            listOf(0 to 4, 1 to 2),
            regions("(* a", "   (* b", "   *)", "   c ( [", "*)", "let x = 1"),
        )
    }

    @Test
    fun stringsInCommentsAreSkipped() {
        assertEquals(listOf(0 to 1), regions("(* \"*)\" '\"'", "  *)", "let x = \")\""))
    }

    @Test
    fun quotedStringsAreSkipped() {
        assertEquals(
            listOf(1 to 2),
            regions("", "  let s = {sql|", "  select ( |sql ) end", "|sql} in"),
        )
    }

    @Test
    fun charactersAreSkipped() {
        assertEquals(
            listOf(1 to 3),
            regions("", "  f (", "    '(' '\\'' ')' 'a' x' (fun (x : 'a) -> x)", "  )"),
        )
    }

    @Test
    fun anEndWithoutAStartIsIgnored() {
        assertEquals(listOf(1 to 3), regions("", "  begin", "    ) ]", "  end"))
    }

    @Test
    fun anEndClosesWhatIsOpenWithinIt() {
        assertEquals(listOf(1 to 3), regions("", "  begin", "    f (", "  end"))
    }

    @Test
    fun anUnclosedBlockHasNoRegion() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("", "  begin", "    f ()"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions(""))
    }

    @Test
    fun aTrailingNewlineDoesNotMatter() {
        assertEquals(listOf(0 to 1), regions("let x =", "  1", ""))
    }

    @Test
    fun brokenTextDoesNotCrash() {
        val sample = listOf(
            "(* a (* b *) \"*)\" *)", "module M = struct", "  let s = {x|", "|x} and c = '\\'' and d = '\"'",
            "  let l = [| (1, [2]); {a = 3} |]", "  let f = begin \"\\", "\" end", "end", "type 'a t = 'a list",
        ).joinToString("\n")
        assertValidForAllPrefixes(sample) { OCamlFoldProvider.foldRegions(it) }
        assertValidForRandomText("(*)[|]{}\"'\\ az\n") { OCamlFoldProvider.foldRegions(it) }
        assertValidForRandomText("letbginsrucd(*) \n") { OCamlFoldProvider.foldRegions(it) }
    }
}
