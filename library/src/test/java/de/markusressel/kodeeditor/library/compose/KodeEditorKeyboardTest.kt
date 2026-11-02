package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

/**
 * What the editor does with the keys of a hardware keyboard.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class KodeEditorKeyboardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var value by mutableStateOf(TextFieldValue())

    /** also matches a read-only editor, which can not set text */
    private val textField = SemanticsMatcher.keyIsDefined(SemanticsActions.SetSelection)

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private fun show(text: String, selection: TextRange = TextRange(0), readOnly: Boolean = false) {
        value = TextFieldValue(text, selection)
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.fillMaxSize().testTag("editor"),
                text = value,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onValueChange = { value = it },
                readOnly = readOnly,
            )
        }
        composeRule.onNode(textField).performClick()
        composeRule.waitForIdle()
        value = value.copy(selection = selection)
        composeRule.waitForIdle()
    }

    private fun keys(block: androidx.compose.ui.test.KeyInjectionScope.() -> Unit) {
        composeRule.onNode(textField).performKeyInput(block)
        composeRule.waitForIdle()
    }

    @Test
    fun arrowsMoveTheCursor() {
        show("abc\ndef", TextRange(1))
        keys { pressKey(Key.DirectionRight) }
        assertEquals(TextRange(2), value.selection)
        keys { pressKey(Key.DirectionLeft) }
        assertEquals(TextRange(1), value.selection)
    }

    @Test
    fun upAndDownChangeTheLine() {
        show("abc\ndef\nghi", TextRange(5))
        keys { pressKey(Key.DirectionDown) }
        assertTrue("${value.selection}", value.selection.end in 8..11)
        keys { pressKey(Key.DirectionUp) }
        keys { pressKey(Key.DirectionUp) }
        assertTrue("${value.selection}", value.selection.end in 0..3)
    }

    @Test
    fun shiftSelects() {
        show("abc", TextRange(1))
        keys { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionRight) } }
        assertEquals(TextRange(1, 2), value.selection)
    }

    @Test
    fun ctrlMovesByWord() {
        show("foo bar", TextRange(0))
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.DirectionRight) } }
        assertEquals(TextRange(3), value.selection)
    }

    @Test
    fun aReadOnlyEditorCanBeNavigated() {
        show("abc", TextRange(1), readOnly = true)
        keys { pressKey(Key.DirectionRight) }
        assertEquals(TextRange(2), value.selection)
    }

    @Test
    fun tabInsertsATab() {
        show("abc", TextRange(1))
        keys { pressKey(Key.Tab) }
        assertEquals("a\tbc", value.text)
        assertEquals(TextRange(2), value.selection)
    }

    @Test
    fun tabChangesNothingInAReadOnlyEditor() {
        show("abc", TextRange(1), readOnly = true)
        keys { pressKey(Key.Tab) }
        assertEquals("abc", value.text)
    }

    @Test
    fun shiftTabInsertsNothing() {
        show("abc", TextRange(1))
        keys { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        assertEquals("abc", value.text)
    }

    @Test
    fun endAndBackspaceWork() {
        show("abc\ndef", TextRange(1))
        keys { pressKey(Key.MoveEnd) }
        assertEquals(TextRange(3), value.selection)

        keys { pressKey(Key.Backspace) }
        assertEquals("ab\ndef", value.text)
    }

    @Test
    fun keysTypeTheirCharacter() {
        show("", TextRange(0))
        keys {
            pressKey(Key.A)
            withKeyDown(Key.ShiftLeft) { pressKey(Key.B) }
            pressKey(Key.Enter)
        }
        assertEquals("aB\n", value.text)
    }

    @Test
    fun aReadOnlyEditorTypesNothing() {
        show("abc", TextRange(1), readOnly = true)
        keys {
            pressKey(Key.A)
            pressKey(Key.Backspace)
            pressKey(Key.Enter)
        }
        assertEquals("abc", value.text)
    }

    @Test
    fun undoAndRedoByKeys() {
        show("abc", TextRange(3))
        keys { pressKey(Key.Delete) }
        keys { pressKey(Key.Backspace) }
        assertEquals("ab", value.text)
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        assertEquals("abc", value.text)
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.Y) } }
        assertEquals("ab", value.text)
    }

    @Test
    fun selectAllAndDelete() {
        show("abc", TextRange(1))
        keys { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        assertEquals(TextRange(0, 3), value.selection)
        keys { pressKey(Key.Delete) }
        assertEquals("", value.text)
    }
}
