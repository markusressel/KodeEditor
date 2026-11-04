package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class InlineFoldLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private lateinit var layout: KodeEditorLayout

    // a character is a single pixel in these tests
    private val target = "https://example.com/" + "long/".repeat(600)
    private val text = "go [x]($target) on\nnext"
    private val start = text.indexOf('h')
    private val end = text.indexOf(") on")

    private fun show(state: KodeEditorState, lineWrapping: Boolean) {
        composeRule.setContent {
            KodeEditor(
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                modifier = Modifier.size(300.dp, 300.dp),
                gutterColumns = emptyList(),
                foldProvider = MarkdownFoldProvider,
                lineWrapping = lineWrapping,
                animations = KodeEditorAnimations.None,
                overlay = { layout = it },
            )
        }
        composeRule.waitForIdle()
    }

    private fun stateAwayFromFold() = KodeEditorState(text).also { it.setSelection(TextRange(text.length)) }

    @Test
    fun whatFollowsAFoldMovesCloserToItsStart() {
        val state = stateAwayFromFold()
        show(state, lineWrapping = false)
        composeRule.runOnIdle {
            val box = layout.getCursorRect(start).left
            val after = layout.getCursorRect(end).left
            assertTrue(after > box)
            // about one character wide, instead of the whole address
            assertTrue(after - box < layout.getCursorRect(start).left)
        }
    }

    @Test
    fun aPositionWithinAFoldIsAtTheStartOfTheBox() {
        val state = stateAwayFromFold()
        show(state, lineWrapping = false)
        composeRule.runOnIdle {
            assertEquals(layout.getCursorRect(start).left, layout.getCursorRect(start + 10).left)
        }
    }

    @Test
    fun aCollapsedLineTakesOneRowAndAnOpenOneMore() {
        val state = stateAwayFromFold()
        show(state, lineWrapping = true)
        composeRule.runOnIdle {
            assertEquals(layout.lineHeight, layout.getLineBottom(0) - layout.getLineTop(0))
            state.setSelection(TextRange(start))
        }
        composeRule.runOnIdle {
            assertTrue(layout.getLineBottom(0) - layout.getLineTop(0) > layout.lineHeight)
            state.setSelection(TextRange(text.length))
        }
        composeRule.runOnIdle {
            assertEquals(layout.lineHeight, layout.getLineBottom(0) - layout.getLineTop(0))
        }
    }

    @Test
    fun theDocumentIsNotWiderThanWhatIsShown() {
        val state = stateAwayFromFold()
        show(state, lineWrapping = false)
        composeRule.runOnIdle {
            val closed = layout.contentSize.width
            state.setSelection(TextRange(start))
            composeRule.runOnIdle { assertTrue(layout.contentSize.width > closed * 2) }
        }
    }

    @Test
    fun editingInFrontOfAFoldKeepsEverythingInPlace() {
        val state = stateAwayFromFold()
        show(state, lineWrapping = true)
        composeRule.runOnIdle {
            state.setSelection(TextRange(0))
            state.replaceSelection("abc ")
        }
        composeRule.runOnIdle {
            assertEquals(layout.lineHeight, layout.getLineBottom(0) - layout.getLineTop(0))
            assertEquals(text.length + 4, state.rawText.length)
        }
    }
}
