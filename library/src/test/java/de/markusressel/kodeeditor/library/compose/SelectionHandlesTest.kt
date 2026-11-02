package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import org.robolectric.annotation.GraphicsMode

/** the fake graphics of Robolectric have no real text positions, which dragging a handle depends on */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class SelectionHandlesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val field = SemanticsMatcher.keyIsDefined(SemanticsActions.SetSelection)
    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }
    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }
    private var value by mutableStateOf(TextFieldValue())
    private val menus = mutableListOf<Rect>()
    private val toolbar = object : TextToolbar {
        override var status = TextToolbarStatus.Hidden
        override fun hide() {
            status = TextToolbarStatus.Hidden
        }

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?,
        ) {
            status = TextToolbarStatus.Shown
            menus += rect
        }
    }

    private fun show(text: String, readOnly: Boolean = false) {
        value = TextFieldValue(text)
        composeRule.setContent {
            CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                KodeEditor(
                    modifier = Modifier.size(400.dp),
                    text = value,
                    languageRuleBook = noRules,
                    colorScheme = noColors,
                    onValueChange = { value = it },
                    readOnly = readOnly,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun tap() {
        composeRule.onNode(field).performClick()
        composeRule.waitForIdle()
    }

    private val manyLines = (1..100).joinToString("\n") { "line $it with some text" }

    @Test
    fun `a tap shows the cursor handle`() {
        show(manyLines)
        composeRule.onNodeWithTag(CURSOR_HANDLE_TAG).assertDoesNotExist()
        tap()
        composeRule.onNodeWithTag(CURSOR_HANDLE_TAG).assertExists()
    }

    @Test
    fun `typing hides the cursor handle again`() {
        show(manyLines)
        tap()
        composeRule.onNode(field).performTextInputCompat("x")
        composeRule.onNodeWithTag(CURSOR_HANDLE_TAG).assertDoesNotExist()
    }

    @Test
    fun `a read-only editor has no cursor handle`() {
        show(manyLines, readOnly = true)
        tap()
        composeRule.onNodeWithTag(CURSOR_HANDLE_TAG).assertDoesNotExist()
    }

    @Test
    fun `dragging the cursor handle moves the cursor`() {
        show(manyLines)
        tap()
        val before = value.selection
        val tag = composeRule.onNodeWithTag(CURSOR_HANDLE_TAG)
        tag.performTouchInput { swipe(center, center + Offset(30f, 200f), 300) }
        composeRule.waitForIdle()
        assertTrue(value.selection.collapsed)
        assertTrue("selection stayed at $before", value.selection != before)
    }

    @Test
    fun `a selection has two handles that change it, and a menu`() {
        show(manyLines)
        tap()
        value = value.copy(selection = TextRange(30, 60))
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertTrue(menus.isNotEmpty())
        composeRule.onNodeWithTag(SELECTION_START_HANDLE_TAG).assertExists()
        composeRule.onNodeWithTag(SELECTION_END_HANDLE_TAG).performTouchInput {
            swipe(center, center + Offset(0f, 150f), 300)
        }
        composeRule.waitForIdle()
        assertEquals(30, value.selection.min)
        assertTrue("selection ends at ${value.selection.max}", value.selection.max > 60)
    }

    @Test
    fun `a handle near an edge scrolls, faster when it is closer`() {
        val area = Size(1000f, 2000f)
        assertEquals(Offset.Zero, HandleDrag.edgeScroll(Offset(500f, 1000f), area, 100f, 50f, 10f))
        assertEquals(Offset(0f, 5f), HandleDrag.edgeScroll(Offset(500f, 1975f), area, 100f, 50f, 10f))
        assertEquals(Offset(0f, 10f), HandleDrag.edgeScroll(Offset(500f, 2500f), area, 100f, 50f, 10f))
        assertEquals(Offset(-10f, 0f), HandleDrag.edgeScroll(Offset(90f, 1000f), area, 100f, 50f, 10f))
    }

    @Test
    fun `the menu actions change the text and the clipboard`() {
        var clip: AnnotatedString? = null
        val state = KodeEditorState("hello world", TextRange(6, 11))
        val menu = HandleMenu(state, false, { clip }, { clip = it })

        menu.cut()
        assertEquals("world", clip?.text)
        assertEquals("hello ", state.text)
        assertEquals(TextRange(6), state.selection)

        state.setSelection(TextRange(0))
        menu.paste()
        assertEquals("worldhello ", state.text)
        assertEquals(TextRange(5), state.selection)

        menu.selectAll()
        assertEquals(TextRange(0, 11), state.selection)

        menu.copy()
        assertEquals("worldhello ", clip?.text)
        assertEquals(TextRange(11), state.selection)
    }

    @Test
    fun `a double tap selects the word`() {
        show("one two three")
        val center = composeRule.onNode(field)
        center.performTouchInput {
            click(Offset(5f, 5f))
            advanceEventTime(50)
            click(Offset(5f, 5f))
        }
        composeRule.waitForIdle()
        assertEquals(TextRange(0, 3), value.selection)
    }

    @Test
    fun `a long press selects the word`() {
        show("one two three")
        composeRule.onNode(field).performTouchInput {
            down(Offset(5f, 5f))
            advanceEventTime(1000)
            move()
            up()
        }
        composeRule.waitForIdle()
        assertEquals(TextRange(0, 3), value.selection)
    }

    @Test
    fun `dragging after a long press extends the selection`() {
        show("one two three")
        composeRule.onNode(field).performTouchInput {
            down(Offset(5f, 5f))
            advanceEventTime(1000)
            move()
            moveTo(Offset(1000f, 5f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(TextRange(0, 13), value.selection)
    }

    /** the offset was only taken from the editor when it changed, so a pan beyond the end had to be undone first */
    @Test
    fun `a pan beyond the end of the text can be taken back at once`() {
        show(manyLines)
        tap()
        val editor = composeRule.onNode(field)
        repeat(4) { editor.performTouchInput { swipe(center, center - Offset(0f, 900f), 1000) } }
        composeRule.waitForIdle()

        editor.performTouchInput { click(Offset(5f, 5f)) }
        composeRule.waitForIdle()
        val atEnd = value.selection.start
        assertTrue("the last lines should be visible, tapped at $atEnd", atEnd > manyLines.length / 2)

        editor.performTouchInput { swipe(center, center + Offset(0f, 300f), 1000) }
        composeRule.waitForIdle()
        editor.performTouchInput { click(Offset(5f, 5f)) }
        composeRule.waitForIdle()
        assertTrue("view stayed at the end, tapped at ${value.selection.start}", value.selection.start < atEnd)
    }

    @Test
    fun `a fast swipe keeps moving`() {
        show(manyLines)
        tap()
        val editor = composeRule.onNode(field)
        editor.performTouchInput { swipeUp(startY = centerY + 100f, endY = centerY - 100f, durationMillis = 50) }
        composeRule.waitForIdle()
        editor.performTouchInput { click(Offset(5f, 5f)) }
        composeRule.waitForIdle()
        // a slow pan of that distance stays within the first lines
        val tappedAt = value.selection.start
        assertTrue("moved to $tappedAt", tappedAt > manyLines.indexOf("line 15 "))
    }

    @Test
    fun `a touch during a fling only stops it`() {
        show(manyLines)
        tap()
        val before = value.selection
        val editor = composeRule.onNode(field)
        composeRule.mainClock.autoAdvance = false
        editor.performTouchInput { swipeUp(startY = centerY + 100f, endY = centerY - 100f, durationMillis = 50) }
        composeRule.mainClock.advanceTimeBy(100)
        editor.performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(1000)
        assertEquals(before, value.selection)
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun `panning does not move the cursor`() {
        show(manyLines)
        tap()
        val before = value.selection
        composeRule.onNode(field).performTouchInput { swipe(center, center - Offset(0f, 300f), 200) }
        composeRule.waitForIdle()
        assertEquals(before, value.selection)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextInputCompat(text: String) {
        fetchSemanticsNode().config[SemanticsActions.InsertTextAtCursor].action?.invoke(AnnotatedString(text))
        composeRule.waitForIdle()
    }
}
