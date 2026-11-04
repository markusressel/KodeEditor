package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.random.Random

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

    @Test
    fun randomChangesKeepARowLayoutConsistent() = randomChanges(lineWrapping = false)

    @Test
    fun randomChangesKeepAWrappedLayoutConsistent() = randomChanges(lineWrapping = true)

    private fun randomChanges(lineWrapping: Boolean) {
        val pieces = listOf("[x](https://example.com/abcdef)", " word ", "\n", "&rarr;", ")", "(", "[", "]", "😀", "<!-- a long comment -->")
        val random = Random(lineWrapping.hashCode())
        val state = KodeEditorState(pieces.joinToString(""))
        lateinit var current: KodeEditorLayout
        composeRule.setContent { Editor(state, it = { current = it }, wrap = lineWrapping) }
        composeRule.waitForIdle()
        repeat(RANDOM_STEPS) {
            composeRule.runOnIdle {
                val length = state.rawText.length
                val position = random.nextInt(length + 1)
                when (random.nextInt(5)) {
                    0 -> state.setSelection(TextRange(position))
                    1 -> state.setSelection(TextRange(position, random.nextInt(length + 1)))
                    2 -> state.applyRemote(listOf(TextSplice(position, 0, pieces.random(random))))
                    3 -> state.applyRemote(listOf(TextSplice(position, minOf(random.nextInt(8), length - position), "")))
                    else -> {
                        state.setSelection(TextRange(position))
                        state.replaceSelection(pieces.random(random))
                    }
                }
            }
            composeRule.runOnIdle {
                val shown = current.contentSize.height / current.lineHeight
                val text = state.rawText
                var previousTop = 0f
                for (offset in 0..text.length step 3) {
                    val rect = current.getCursorRect(offset)
                    assertTrue(rect.top >= previousTop)
                    assertTrue(rect.top < current.contentSize.height.toFloat() + 1f)
                    previousTop = rect.top
                }
                for (row in 0 until shown.toInt() step 2) {
                    val offset = current.getOffsetForPosition(Offset(5f, row * current.lineHeight + 1f))
                    assertTrue(offset in 0..text.length)
                }
                current.getPathForRange(0, text.length)
            }
        }
    }

    @Composable
    private fun Editor(state: KodeEditorState, it: (KodeEditorLayout) -> Unit, wrap: Boolean) {
        KodeEditor(
            state = state,
            languageRuleBook = noRules,
            colorScheme = noColors,
            modifier = Modifier.size(300.dp, 300.dp),
            gutterColumns = emptyList(),
            foldProvider = MarkdownFoldProvider,
            lineWrapping = wrap,
            overlay = { layout -> it(layout) },
        )
    }

    private companion object {
        const val RANDOM_STEPS = 400
    }
}
