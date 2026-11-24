package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What accessibility services see of the editor and what they can do with it */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class EditorSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private val field = SemanticsMatcher.keyIsDefined(SemanticsActions.SetSelection)

    private fun show(state: KodeEditorState, enabled: Boolean = true, readOnly: Boolean = false) {
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                enabled = enabled,
                readOnly = readOnly,
            )
        }
        composeRule.waitForIdle()
    }

    private fun config() = composeRule.onNode(field).fetchSemanticsNode().config

    @Test
    fun `the text and the selection are shown to accessibility services`() {
        val state = KodeEditorState("hello world", TextRange(2, 5))
        show(state)

        assertEquals(AnnotatedString("hello world"), config()[SemanticsProperties.EditableText])
        assertEquals(TextRange(2, 5), config()[SemanticsProperties.TextSelectionRange])
    }

    @Test
    fun `the selection can be set within the text only`() {
        val state = KodeEditorState("hello")
        show(state)
        val setSelection = config()[SemanticsActions.SetSelection].action!!

        composeRule.runOnIdle {
            assertTrue(setSelection(1, 3, false))
            assertFalse(setSelection(0, 9, false))
        }
        assertEquals(TextRange(1, 3), state.selection)
    }

    @Test
    fun `text can be set and inserted when it can be edited`() {
        val state = KodeEditorState("hello", TextRange(5))
        show(state)

        composeRule.runOnIdle {
            config()[SemanticsActions.InsertTextAtCursor].action!!(AnnotatedString(" world"))
        }
        assertEquals("hello world", state.text)
        composeRule.runOnIdle { config()[SemanticsActions.SetText].action!!(AnnotatedString("new")) }
        assertEquals("new", state.text)
    }

    @Test
    fun `a read-only editor offers no text to set`() {
        show(KodeEditorState("hello"), readOnly = true)

        assertNull(config().getOrNull(SemanticsActions.SetText))
        assertNull(config().getOrNull(SemanticsActions.InsertTextAtCursor))
    }

    @Test
    fun `a disabled editor is marked disabled and does not take a selection`() {
        val state = KodeEditorState("hello")
        show(state, enabled = false)

        assertTrue(config().contains(SemanticsProperties.Disabled))
        composeRule.runOnIdle { assertFalse(config()[SemanticsActions.SetSelection].action!!(1, 2, false)) }
        assertEquals(TextRange(0), state.selection)
    }
}
