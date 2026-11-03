package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class LineDecorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private class TestDecoration(private val line: Int, private val takesTaps: Boolean = true) :
        KodeEditorLineDecoration {
        val tapped = mutableListOf<Int>()
        val drawn = mutableSetOf<Int>()

        override fun DrawScope.drawBackground(line: DecoratedLine) {
            drawn += line.line
        }

        override fun trailingText(line: Int) = if (line == this.line) AnnotatedString("note") else null

        override fun onTrailingTextTap(line: Int): Boolean {
            tapped += line
            return takesTaps
        }
    }

    private lateinit var layout: KodeEditorLayout

    private fun show(
        state: KodeEditorState,
        decorations: List<KodeEditorLineDecoration> = emptyList(),
        foldProvider: KodeEditorFoldProvider? = null,
    ) {
        composeRule.setContent {
            KodeEditor(
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                modifier = Modifier.size(300.dp, 300.dp).testTag("editor"),
                gutterColumns = emptyList(),
                foldProvider = foldProvider,
                lineDecorations = decorations,
                overlay = { layout = it },
            )
        }
        // the first tap would be a double tap otherwise, at the start of the clock
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
    }

    /** the padding in front of the text is a few pixels, this is further than that and within the slack of a tap */
    private fun tapBehindLine(line: Int, distance: Float = 20f) {
        val point = Offset(
            x = layout.getCursorRect(layout.getLineEnd(line)).right + distance,
            y = layout.getLineTop(line) + layout.lineHeight / 2,
        )
        composeRule.onNodeWithTag("editor").performTouchInput { down(point); up() }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap on the text behind a line goes to its decoration`() {
        val decoration = TestDecoration(line = 1)
        val state = KodeEditorState("ab\ncd\nef")
        show(state, listOf(decoration))
        tapBehindLine(1)
        assertEquals(listOf(1), decoration.tapped)
        assertEquals(TextRange(0), state.selection)
    }

    @Test
    fun `a tap behind a line without text places the cursor`() {
        val decoration = TestDecoration(line = 1)
        val state = KodeEditorState("ab\ncd\nef")
        show(state, listOf(decoration))
        tapBehindLine(0)
        assertEquals(emptyList<Int>(), decoration.tapped)
        assertEquals(TextRange(2), state.selection)
    }

    @Test
    fun `a decoration that does not take the tap leaves it to the editor`() {
        val decoration = TestDecoration(line = 1, takesTaps = false)
        val state = KodeEditorState("ab\ncd\nef")
        show(state, listOf(decoration))
        tapBehindLine(1)
        assertEquals(listOf(1), decoration.tapped)
        assertEquals(TextRange(5), state.selection)
    }

    @Test
    fun `the placeholder of a folded line unfolds it`() {
        val state = KodeEditorState("# a\n1\n2\n\n# b\n3")
        show(state, foldProvider = MarkdownFoldProvider)
        assertFalse(layout.isLineHidden(1))

        // the column of the arrows is the whole bar
        val y = layout.getLineTop(0) + layout.lineHeight / 2
        composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(10f, y)); up() }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertTrue(layout.isLineHidden(1))
        assertTrue(layout.isLineHidden(2))
        assertFalse(layout.isLineHidden(3))

        tapBehindLine(0)
        assertFalse(layout.isLineHidden(1))
    }
}
