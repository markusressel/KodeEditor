package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.SpanStyle
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
class FoldAnimationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private lateinit var layout: KodeEditorLayout

    private fun show(state: KodeEditorState, animations: KodeEditorAnimations, lineWrapping: Boolean = false) {
        composeRule.setContent {
            KodeEditor(
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                modifier = Modifier.size(300.dp, 300.dp).testTag("editor"),
                gutterColumns = emptyList(),
                foldProvider = MarkdownFoldProvider,
                lineWrapping = lineWrapping,
                animations = animations,
                overlay = { layout = it },
            )
        }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
    }

    private fun tapArrow(line: Int) {
        val y = layout.getLineTop(line) + layout.lineHeight / 2
        composeRule.onNodeWithTag("editor").performTouchInput { down(Offset(10f, y)); up() }
        composeRule.waitForIdle()
    }

    private fun checkMoves(lineWrapping: Boolean) {
        show(KodeEditorState("# a\n1\n2\n\n# b\n3"), KodeEditorAnimations(), lineWrapping)
        val height = layout.lineHeight
        assertEquals(3 * height, layout.getLineTop(3), 0.01f)

        composeRule.mainClock.autoAdvance = false
        tapArrow(0)
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        val moving = layout.getLineTop(3)
        assertTrue("$moving is on its way", moving > height && moving < 3 * height)

        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(height, layout.getLineTop(3), 0.01f)

        // and back
        composeRule.mainClock.autoAdvance = false
        tapArrow(0)
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        val back = layout.getLineTop(3)
        assertTrue("$back is on its way", back > height && back < 3 * height)
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(3 * height, layout.getLineTop(3), 0.01f)
    }

    @Test
    fun `the lines below a fold move to their new place`() = checkMoves(lineWrapping = false)

    @Test
    fun `with line wrapping the lines below a fold move to their new place`() = checkMoves(lineWrapping = true)

    @Test
    fun `without the animation the lines jump`() {
        show(KodeEditorState("# a\n1\n2\n\n# b\n3"), KodeEditorAnimations(folding = false))
        composeRule.mainClock.autoAdvance = false
        tapArrow(0)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        assertEquals(layout.lineHeight, layout.getLineTop(3), 0.01f)
    }
}
