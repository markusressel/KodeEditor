package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAnimations
import de.markusressel.kodeeditor.library.compose.fold.KodeEditorFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.MarkdownFoldProvider
import de.markusressel.kodeeditor.library.compose.layout.KodeEditorLayout
import de.markusressel.kodeeditor.library.compose.search.KodeEditorSearch
import de.markusressel.kodeeditor.library.compose.search.KodeEditorSearchQuery
import de.markusressel.kodeeditor.library.compose.search.rememberKodeEditorSearch
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class KodeEditorSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private lateinit var search: KodeEditorSearch
    private val viewState = KodeEditorViewState()
    private var layout: KodeEditorLayout? = null

    private fun show(
        state: KodeEditorState,
        readOnly: Boolean = false,
        foldProvider: KodeEditorFoldProvider? = null,
        matchColors: Pair<Color, Color>? = null,
    ) {
        composeRule.setContent {
            search = rememberKodeEditorSearch(state)
            KodeEditor(
                modifier = Modifier.size(400.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                readOnly = readOnly,
                onTextLayout = { layout = it },
                foldProvider = foldProvider,
                animations = KodeEditorAnimations.None,
                colors = KodeEditorDefaults.editorColors(
                    searchMatchBackgroundColor = matchColors?.first ?: Color.Unspecified,
                    currentSearchMatchBackgroundColor = matchColors?.second ?: Color.Unspecified,
                ),
                viewState = viewState,
                search = search,
            )
        }
        composeRule.waitForIdle()
    }

    /** sets the query, and waits until the text is searched */
    private fun find(query: KodeEditorSearchQuery?) {
        composeRule.runOnIdle { search.query = query }
        settle()
    }

    private fun find(text: String) = find(KodeEditorSearchQuery(text))

    /** waits until the search of the current text and query is done */
    private fun settle() {
        composeRule.waitForIdle()
        composeRule.waitUntil(5000) { !search.isSearching }
        composeRule.waitForIdle()
    }

    private fun KodeEditorState.rangeOf(text: String, from: Int = 0): TextRange {
        val start = this.text.indexOf(text, from)
        assertTrue("$text is in the text", start >= 0)
        return TextRange(start, start + text.length)
    }

    @Test
    fun `nothing is found without a query`() {
        show(KodeEditorState("a b a"))

        assertEquals(emptyList<TextRange>(), search.matches)
        assertNull(search.current)
        assertNull(search.next())

        find("")
        assertEquals(emptyList<TextRange>(), search.matches)
    }

    @Test
    fun `all matches are found in order, ignoring the case`() {
        show(KodeEditorState("Apple and apple and APPLE"))

        find("apple")

        assertEquals(listOf(TextRange(0, 5), TextRange(10, 15), TextRange(20, 25)), search.matches)
        assertNull("no current match before next", search.current)
        assertNull(search.error)
        assertFalse(search.isLimited)
    }

    @Test
    fun `the case can count`() {
        show(KodeEditorState("Apple and apple"))

        find(KodeEditorSearchQuery("apple", ignoreCase = false))

        assertEquals(listOf(TextRange(10, 15)), search.matches)
    }

    @Test
    fun `the case of letters beyond ascii is ignored too`() {
        show(KodeEditorState("Über über"))

        find("ÜBER")

        assertEquals(2, search.matches.size)
    }

    @Test
    fun `whole words only match on their own`() {
        val state = KodeEditorState("cat catalog bobcat cat_x cat.")
        show(state)

        find(KodeEditorSearchQuery("cat", wholeWord = true))

        val last = state.rangeOf("cat.").min
        assertEquals(listOf(TextRange(0, 3), TextRange(last, last + 3)), search.matches)
    }

    @Test
    fun `a regular expression finds its matches`() {
        show(KodeEditorState("# one\ntext\n## two"))

        find(KodeEditorSearchQuery("^#+ \\w+$", regex = true))

        assertEquals(listOf(TextRange(0, 5), TextRange(11, 17)), search.matches)
    }

    @Test
    fun `the text of a query is not a regular expression without regex`() {
        show(KodeEditorState("a.c abc"))

        find("a.c")

        assertEquals(listOf(TextRange(0, 3)), search.matches)
    }

    @Test
    fun `an invalid regular expression is reported and finds nothing`() {
        show(KodeEditorState("abc"))

        find(KodeEditorSearchQuery("(a", regex = true))

        assertEquals(emptyList<TextRange>(), search.matches)
        assertNotNull(search.error)

        find(KodeEditorSearchQuery("a", regex = true))
        assertNull(search.error)
        assertEquals(1, search.matches.size)
    }

    @Test
    fun `a regular expression that matches nothing visible finds nothing`() {
        show(KodeEditorState("abc"))

        find(KodeEditorSearchQuery("x*", regex = true))

        assertEquals(emptyList<TextRange>(), search.matches)
    }

    @Test
    fun `too many matches are cut off`() {
        show(KodeEditorState("a".repeat(KodeEditorSearch.MAX_MATCHES + 5)))

        find("a")

        assertEquals(KodeEditorSearch.MAX_MATCHES, search.matches.size)
        assertTrue(search.isLimited)
    }

    @Test
    fun `next and previous go around and leave the cursor alone`() {
        val state = KodeEditorState("x a x a x a")
        state.setSelection(TextRange(4))
        show(state)
        find("a")
        val (first, second, third) = search.matches

        composeRule.runOnIdle {
            assertEquals("the next one after the cursor", second, search.next())
            assertEquals(1, search.currentIndex)
            assertEquals(third, search.next())
            assertEquals("around to the first", first, search.next())
            assertEquals("around to the last", third, search.previous())
            assertEquals(second, search.previous())
            assertEquals(second, search.current)
            assertEquals(TextRange(4), state.selection)
        }
    }

    @Test
    fun `previous goes from the cursor without a current match`() {
        val state = KodeEditorState("x a x a x a")
        state.setSelection(TextRange(7))
        show(state)
        find("a")

        composeRule.runOnIdle {
            assertEquals(TextRange(6, 7), search.previous())
        }
    }

    @Test
    fun `a new query has no current match`() {
        show(KodeEditorState("a b a b"))
        find("a")
        composeRule.runOnIdle { search.next() }

        find("b")

        assertNull(search.current)
    }

    @Test
    fun `the matches move with remote changes before the text is searched again`() {
        val state = KodeEditorState("one two one")
        show(state)
        find("one")
        composeRule.runOnIdle { search.next(0) }

        composeRule.runOnIdle {
            state.applyRemote(listOf(TextSplice(0, 0, "zero ")))
            // right away, in the same call
            assertEquals(listOf(TextRange(5, 8), TextRange(13, 16)), search.matches)
            assertEquals(TextRange(5, 8), search.current)
        }
        settle()

        assertEquals(listOf(TextRange(5, 8), TextRange(13, 16)), search.matches)
        assertEquals("the current match stays", TextRange(5, 8), search.current)
    }

    @Test
    fun `remote text that matches is found`() {
        val state = KodeEditorState("one")
        show(state)
        find("one")

        composeRule.runOnIdle { state.applyRemote(listOf(TextSplice(3, 0, " and one"))) }
        settle()

        assertEquals(listOf(TextRange(0, 3), TextRange(8, 11)), search.matches)
    }

    @Test
    fun `a current match that is changed away gives its place to the next one`() {
        val state = KodeEditorState("one two one three one")
        show(state)
        find("one")
        composeRule.runOnIdle { search.next(5) }
        assertEquals(TextRange(8, 11), search.current)

        composeRule.runOnIdle { state.applyRemote(listOf(TextSplice(8, 3, "uno"))) }
        settle()

        assertEquals(listOf(TextRange(0, 3), TextRange(18, 21)), search.matches)
        assertEquals(TextRange(18, 21), search.current)
    }

    @Test
    fun `text that is hidden by a transparent range style is not found until it is shown`() {
        val state = KodeEditorState("one two one")
        show(state)
        val hidden = AnnotatedString.Range(SpanStyle(color = Color.Transparent), 6, 11, "agent")
        composeRule.runOnIdle { state.setRangeStyles(listOf(hidden)) }
        find("one")

        assertEquals(listOf(TextRange(0, 3)), search.matches)

        composeRule.runOnIdle { state.setRangeStyles(listOf(hidden.copy(start = 9))) }
        assertEquals("partly hidden is still hidden", listOf(TextRange(0, 3)), search.matches)

        composeRule.runOnIdle { state.setRangeStyles(emptyList()) }
        assertEquals(listOf(TextRange(0, 3), TextRange(8, 11)), search.matches)
    }

    @Test
    fun `a style that is not transparent does not hide matches`() {
        val state = KodeEditorState("one")
        show(state)
        composeRule.runOnIdle {
            state.setRangeStyles(listOf(AnnotatedString.Range(SpanStyle(color = Color.Red), 0, 3, "")))
        }
        find("one")

        assertEquals(1, search.matches.size)
    }

    @Test
    fun `going to a match in a fold unfolds it`() {
        val text = "# a\nhidden one\n# b\nshown"
        val state = KodeEditorState(text)
        show(state, foldProvider = MarkdownFoldProvider)
        composeRule.runOnIdle { viewState.setFolded(0, true) }
        composeRule.waitForIdle()
        assertTrue(layout!!.isLineHidden(1))
        find("one")
        assertEquals("matches in folds count", 1, search.matches.size)

        composeRule.runOnIdle { search.next() }
        composeRule.waitForIdle()

        assertFalse(viewState.isFolded(0))
        assertFalse(layout!!.isLineHidden(1))
    }

    @Test
    fun `a folded part of a line is open while it has the current match`() {
        val state = KodeEditorState("see [the docs](https://example.com/page) here\nother")
        show(state, foldProvider = MarkdownFoldProvider)
        val url = state.rangeOf("example")
        assertNotNull("the address is folded", layout!!.collapsedRangeAt(url.min))
        find("example")

        composeRule.runOnIdle { search.next() }
        composeRule.waitForIdle()
        assertNull("open", layout!!.collapsedRangeAt(url.min))

        find(null)
        assertNotNull("folded again", layout!!.collapsedRangeAt(url.min))
    }

    @Test
    fun `the current match is replaced and the next one becomes current`() {
        val state = KodeEditorState("one two one")
        show(state)
        find("one")
        composeRule.runOnIdle { search.next(0) }

        composeRule.runOnIdle { assertTrue(search.replaceCurrent("1")) }
        settle()

        assertEquals("1 two one", state.text)
        assertEquals(listOf(TextRange(6, 9)), search.matches)
        assertEquals(TextRange(6, 9), search.current)

        composeRule.runOnIdle { state.undo() }
        assertEquals("one two one", state.text)
    }

    @Test
    fun `nothing is replaced without a current match`() {
        val state = KodeEditorState("one")
        show(state)
        find("one")

        composeRule.runOnIdle { assertFalse(search.replaceCurrent("1")) }
        assertEquals("one", state.text)
    }

    @Test
    fun `groups of a regular expression go into the replacement`() {
        val state = KodeEditorState("name: Jane, name: Bob")
        show(state)
        find(KodeEditorSearchQuery("name: (\\w+)", regex = true))
        composeRule.runOnIdle { search.next(0) }

        composeRule.runOnIdle { search.replaceCurrent("[$1] \\$") }

        assertEquals("[Jane] $, name: Bob", state.text)
    }

    @Test
    fun `all matches are replaced in one step to undo, also when read-only`() {
        val state = KodeEditorState("a-b-a-c-a")
        show(state, readOnly = true)
        find("a")

        var replaced = 0
        composeRule.runOnIdle { replaced = search.replaceAll("xy") }
        settle()

        assertEquals(3, replaced)
        assertEquals("xy-b-xy-c-xy", state.text)
        assertEquals(emptyList<TextRange>(), search.matches)

        composeRule.runOnIdle { state.undo() }
        assertEquals("a-b-a-c-a", state.text)
    }

    @Test
    fun `replace all leaves hidden text alone`() {
        val state = KodeEditorState("a a a")
        show(state)
        composeRule.runOnIdle {
            state.setRangeStyles(listOf(AnnotatedString.Range(SpanStyle(color = Color.Transparent), 4, 5, "")))
        }
        find("a")

        composeRule.runOnIdle { assertEquals(2, search.replaceAll("b")) }

        assertEquals("b b a", state.text)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the matches and the current one are drawn`() {
        val state = KodeEditorState("xx found xx found")
        show(state, matchColors = Color.Red to Color.Blue)
        find("found")
        composeRule.runOnIdle { search.next(10) }
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag("editor").captureToImage().toPixelMap()
        val layout = layout!!
        val y = (layout.getLineTop(0) + layout.lineHeight / 2).toInt()
        val colorsInRow = (0 until image.width).map { image[it, y] }.toSet()
        assertTrue("a match: $colorsInRow", Color.Red in colorsInRow)
        assertTrue("the current match", Color.Blue in colorsInRow)
    }
}
