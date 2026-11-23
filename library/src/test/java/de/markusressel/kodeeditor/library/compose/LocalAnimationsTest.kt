package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Paste, undo, redo and replacing with the search, with [KodeEditorLocalAnimations.bigChanges] */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class LocalAnimationsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private lateinit var layout: EditorLineLayout

    private fun show(state: KodeEditorState, local: KodeEditorLocalAnimations?) {
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp, 200.dp),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onTextLayout = { layout = it as EditorLineLayout },
                animations = KodeEditorAnimations(appearance = KodeEditorAppearance(local = local)),
                gutterColumns = emptyList(),
            )
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    private fun frame() {
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun KodeEditorState.localRanges() =
        rangeAlphas.filter { it.tag.appearTag() == LOCAL_APPEAR_TAG }.map { it.start to it.end }

    @Test
    fun `what undo restores fades in, and the text behind it slides, with the cursor at its place at once`() {
        val state = KodeEditorState("first line")
        show(state, KodeEditorLocalAnimations(bigChanges = true))
        composeRule.runOnIdle {
            state.setSelection(TextRange(6, 10))
            state.replaceSelection("")
        }
        repeat(30) { frame() }

        composeRule.runOnIdle { state.undo() }
        frame()

        composeRule.runOnIdle {
            assertEquals("the restored text", listOf(6 to 10), state.localRanges())
            val caret = state.selection.end
            assertEquals("the cursor is at its place", layout.getCursorRect(caret), layout.localCursorRect(caret))
        }
    }

    @Test
    fun `a paste moves the text behind it, and the cursor is at its place at once`() {
        val state = KodeEditorState("first line")
        show(state, KodeEditorLocalAnimations(bigChanges = true))

        composeRule.runOnIdle {
            state.setSelection(TextRange(6))
            state.bigChange { state.replaceSelection("pasted ") }
        }
        frame()

        composeRule.runOnIdle {
            assertEquals(listOf(6 to 13), state.localRanges())
            assertNotEquals("the text behind it slides", layout.getCursorRect(17), layout.shownCursorRect(17))
            assertEquals(layout.getCursorRect(13), layout.localCursorRect(13))
        }
        repeat(30) { frame() }
        composeRule.runOnIdle {
            assertTrue("it appeared", state.rangeAlphas.isEmpty())
            assertEquals(layout.getCursorRect(17), layout.shownCursorRect(17))
        }
    }

    @Test
    fun `typing of the user never animates`() {
        val state = KodeEditorState("first line")
        show(state, KodeEditorLocalAnimations(bigChanges = true))

        composeRule.runOnIdle {
            state.setSelection(TextRange(6))
            state.replaceSelection("x")
        }
        frame()

        composeRule.runOnIdle {
            assertTrue(state.rangeAlphas.isEmpty())
            assertEquals(layout.getCursorRect(9), layout.shownCursorRect(9))
        }
    }

    @Test
    fun `without the option, big changes do not animate`() {
        val state = KodeEditorState("first line")
        show(state, null)

        composeRule.runOnIdle {
            state.setSelection(TextRange(6))
            state.bigChange { state.replaceSelection("pasted ") }
        }
        frame()

        composeRule.runOnIdle {
            assertTrue(state.rangeAlphas.isEmpty())
            assertEquals(layout.getCursorRect(17), layout.shownCursorRect(17))
        }
    }
}
