package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownInlineFoldsTest {

    private fun hidden(text: String) = MarkdownFoldProvider.inlineFolds(text).map { text.substring(it.start, it.end) }

    private val target = "https://example.com/a/long/path"

    @Test
    fun foldsTheTargetOfALink() {
        val text = "see [text]($target) here"
        val fold = MarkdownFoldProvider.inlineFolds(text).first()
        assertEquals(target, text.substring(fold.start, fold.end))
        assertEquals("…", fold.placeholder)
    }

    @Test
    fun foldsTheTargetOfAnImageAndOfALinkAroundIt() {
        assertEquals(listOf(target), hidden("![alt]($target)"))
        val nested = hidden("[![alt](${target}1)](${target}2)")
        assertEquals(setOf("${target}1", "${target}2"), nested.toSet())
    }

    @Test
    fun aShortTargetStaysVisible() {
        assertEquals(emptyList<String>(), hidden("[text](a.md)"))
    }

    @Test
    fun targetsWithParenthesesAreFoundCompletely() {
        assertEquals(listOf("https://example.com/a_(b)_c"), hidden("[x](https://example.com/a_(b)_c)"))
    }

    @Test
    fun anAddressKeepsItsHost() {
        val text = "at $target."
        val fold = MarkdownFoldProvider.inlineFolds(text).single()
        assertEquals(target, text.substring(fold.start, fold.end))
        assertEquals("example.com/…", fold.placeholder)
    }

    @Test
    fun aShortAddressStaysVisible() {
        assertEquals(emptyList<String>(), hidden("https://a.co/b"))
    }

    @Test
    fun foldsCommentsWithinALine() {
        assertEquals(listOf(" a longer comment "), hidden("x <!-- a longer comment --> y"))
        assertEquals(emptyList<String>(), hidden("x <!-- a longer comment"))
    }

    @Test
    fun foldsCharacterReferences() {
        val text = "a &rarr; b &unknown; c"
        val fold = MarkdownFoldProvider.inlineFolds(text).single()
        assertEquals("&rarr;", text.substring(fold.start, fold.end))
        assertEquals("→", fold.placeholder)
    }

    @Test
    fun leavesCodeAlone() {
        assertEquals(emptyList<String>(), hidden("`[x]($target)` and ``$target``"))
        assertEquals(emptyList<String>(), hidden("```\n[x]($target)\n```"))
        assertEquals(listOf(target), hidden("```\ncode\n```\n[x]($target)"))
    }

    @Test
    fun anEscapedBracketIsNoLink() {
        assertEquals(emptyList<String>(), hidden("\\[x](abcdefghijklmnop)"))
    }

    @Test
    fun aLinkDoesNotContinueInTheNextLine() {
        assertEquals(emptyList<String>(), hidden("[x](abcdefghijklmnop\n)"))
    }

    @Test
    fun aProviderWithoutInlineFoldsFindsNone() {
        val provider = KodeEditorFoldProvider { emptyList() }
        assertEquals(emptyList<InlineFold>(), provider.inlineFolds("[x]($target)"))
    }
}
