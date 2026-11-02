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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
    fun `text and editor coordinates convert both ways at any zoom`() {
        val transform = EditorTransform(100, 8f, Offset(50f, 300f), 2.5f)
        val point = Offset(120f, 700f)
        assertEquals(Offset((100 + 8 + 120 - 50) * 2.5f, (700 - 300) * 2.5f), transform.toEditor(point))
        val back = transform.toText(transform.toEditor(point))
        assertEquals(point.x, back.x, 0.01f)
        assertEquals(point.y, back.y, 0.01f)
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
        var result = TextFieldValue()
        val menu = { current: TextFieldValue ->
            HandleMenu(current, false, { clip }, { clip = it }, { result = it })
        }
        val selected = TextFieldValue("hello world", TextRange(6, 11))

        menu(selected).cut()
        assertEquals("world", clip?.text)
        assertEquals(TextFieldValue("hello ", TextRange(6)), result)

        menu(TextFieldValue("say ", TextRange(4))).paste()
        assertEquals(TextFieldValue("say world", TextRange(9)), result)

        menu(selected).selectAll()
        assertEquals(TextRange(0, 11), result.selection)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextInputCompat(text: String) {
        fetchSemanticsNode().config[SemanticsActions.InsertTextAtCursor].action?.invoke(AnnotatedString(text))
        composeRule.waitForIdle()
    }
}
