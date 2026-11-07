package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class XmlFoldProviderTest {

    private fun regions(vararg lines: String) =
        XmlFoldProvider.foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }
            .map { it.startLine to it.endLine }

    @Test
    fun anElementFoldsUntilItsEndTag() {
        assertEquals(listOf(0 to 2), regions("<a>", "  <b/>", "</a>"))
    }

    @Test
    fun elementsNest() {
        assertEquals(
            listOf(1 to 9, 2 to 4, 5 to 8),
            regions(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">",
                "    <TextView",
                "        android:id=\"@+id/title\"",
                "        android:text=\"Hello\" />",
                "    <FrameLayout>",
                "        <View android:id=\"@+id/a\" />",
                "        <View android:id=\"@+id/b\" />",
                "    </FrameLayout>",
                "</LinearLayout>",
            ),
        )
    }

    @Test
    fun theEndTagStaysVisibleIfMoreFollowsOnItsLine() {
        assertEquals(listOf(0 to 1), regions("<a>", "x", "</a><b/>"))
    }

    @Test
    fun anElementOnOneLineHasNoRegion() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("<a>x</a>", "<b></b>"))
    }

    @Test
    fun aStartTagOverSeveralLinesFoldsWithItsElement() {
        assertEquals(
            listOf(0 to 4),
            regions("<View", "  a=\"1\"", "  b=\"2\">", "  <c/>", "</View>"),
        )
    }

    @Test
    fun aSelfClosingTagOverSeveralLinesFolds() {
        assertEquals(listOf(0 to 2), regions("<View", "  a=\"1\"", "  b=\"2\" />"))
    }

    @Test
    fun attributesMayContainBrackets() {
        assertEquals(listOf(0 to 2), regions("<a b=\">\" c='</a>'>", "x", "</a>"))
    }

    @Test
    fun commentsFoldAndAreSkipped() {
        assertEquals(listOf(0 to 3, 1 to 2), regions("<a>", "<!-- <b>", "</a> -->", "</a>"))
    }

    @Test
    fun cdataIsSkipped() {
        assertEquals(listOf(0 to 4, 1 to 3), regions("<a>", "<![CDATA[", "</a>", "]]>", "</a>"))
    }

    @Test
    fun processingInstructionsFold() {
        assertEquals(listOf(0 to 2), regions("<?xml-stylesheet", "  href=\"a.xsl\"", "?>", "<a/>"))
    }

    @Test
    fun aDoctypeWithAnInternalSubsetFolds() {
        assertEquals(
            listOf(0 to 3, 4 to 6),
            regions(
                "<!DOCTYPE note [",
                "  <!ELEMENT note (to)>",
                "  <!-- ] > -->",
                "]>",
                "<note>",
                "  <to>x</to>",
                "</note>",
            ),
        )
    }

    @Test
    fun anEndTagClosesTheNearestElementOfItsName() {
        assertEquals(listOf(0 to 3), regions("<a>", "<b>", "x", "</a>", "</b>"))
    }

    @Test
    fun anEndTagWithoutAStartTagIsIgnored() {
        assertEquals(listOf(0 to 3), regions("<a>", "</c>", "x", "</a>"))
    }

    @Test
    fun anUnclosedElementHasNoRegion() {
        assertEquals(listOf(1 to 2), regions("<a>", "<b>", "</b>", "x"))
    }

    @Test
    fun namesAreCaseSensitive() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions("<A>", "x", "</a>"))
    }

    @Test
    fun voidElementsOfHtmlNeedNoEndTag() {
        assertEquals(
            listOf(0 to 4, 5 to 7),
            regions("<div>", "  <br>", "  <img src=\"a.png\">", "  text", "</div>", "<p>", "  y", "</p>"),
        )
    }

    @Test
    fun scriptsOfHtmlAreText() {
        assertEquals(listOf(0 to 2), regions("<script>", "if (a<b) { x = '</a>' }", "</script>"))
    }

    @Test
    fun voidElementsAreElementsInXml() {
        assertEquals(listOf(1 to 3), regions("<?xml version=\"1.0\"?>", "<br>", "x", "</br>"))
    }

    @Test
    fun aLessThanSignInTextIsNoTag() {
        assertEquals(listOf(0 to 2), regions("<a>", "1 < 2", "</a>"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        assertEquals(emptyList<Pair<Int, Int>>(), regions(""))
    }

    @Test
    fun aTrailingNewlineDoesNotMatter() {
        assertEquals(listOf(0 to 2), regions("<a>", "x", "</a>", ""))
    }

    @Test
    fun brokenTextDoesNotCrash() {
        val sample = listOf(
            "<?xml version=\"1.0\"?>", "<!DOCTYPE a [ <!ENTITY b \"c\"> ]>", "<a x='1'", "  y=\"2\">",
            "<!-- c -->", "<![CDATA[ <d> ]]>", "<e/>", "</a>",
        ).joinToString("\n")
        assertValidForAllPrefixes(sample) { XmlFoldProvider.foldRegions(it) }
        assertValidForRandomText("<>/!-?[]\"'= ab\n") { XmlFoldProvider.foldRegions(it) }
    }
}

/** checks the regions of each start of [sample] */
internal fun assertValidForAllPrefixes(sample: String, foldRegions: (String) -> List<FoldRegion>) {
    for (length in 0..sample.length) assertValid(sample.substring(0, length), foldRegions)
}

/** checks the regions of random texts made of [alphabet] */
internal fun assertValidForRandomText(alphabet: String, foldRegions: (String) -> List<FoldRegion>) {
    val random = Random(1)
    repeat(500) {
        val text = String(CharArray(random.nextInt(200)) { alphabet[random.nextInt(alphabet.length)] })
        assertValid(text, foldRegions)
    }
}

private fun assertValid(text: String, foldRegions: (String) -> List<FoldRegion>) {
    val lineCount = text.count { it == '\n' } + 1
    for (region in foldRegions(text)) {
        assertTrue("$region in \"$text\"", region.startLine >= 0 && region.endLine > region.startLine)
        assertTrue("$region in \"$text\"", region.endLine < lineCount)
    }
}
