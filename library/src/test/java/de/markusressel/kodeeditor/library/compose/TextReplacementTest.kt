package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class TextReplacementTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private lateinit var layout: KodeEditorLayout

    // a character is a single pixel in these tests, these lines wrap, and the number of lines makes the bar wider
    private val document = List(200) { "word ".repeat(600) }.joinToString("\n")

    // a bar that gets a lot wider with more lines, so the lines wrap differently
    private val column = object : KodeEditorGutterColumn {
        override fun width(lineCount: Int) = if (lineCount > 1) 200 else 10
        override fun DrawScope.drawCell(cell: GutterCell) = Unit
    }

    private fun show(state: KodeEditorState) {
        composeRule.setContent {
            KodeEditor(
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                modifier = Modifier.size(300.dp, 300.dp),
                lineWrapping = true,
                gutterColumns = listOf(column),
                overlay = { layout = it },
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theFirstTextIsInItsPlaceRightAway() {
        val state = KodeEditorState("")
        show(state)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { state.setText(document) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val offset = document.length / 2
        val first = composeRule.runOnIdle { layout.getCursorRect(offset) }
        composeRule.mainClock.advanceTimeBy(2000)
        val settled = composeRule.runOnIdle { layout.getCursorRect(offset) }
        assertEquals(settled, first)
    }

    @Test
    fun aFirstRunThatTakesLongDoesNotBlockAndIsNotRepeated() = runBlocking {
        val runs = AtomicInteger()
        val release = CountDownLatch(1)
        val state = KodeEditorState("some text")
        val highlighting = EditorHighlighting(
            state = state,
            languageRuleBook = object : LanguageRuleBook {
                override fun getRules() = listOf<LanguageRule>(object : LanguageRule {
                    override fun findMatches(text: CharSequence): List<RuleMatch> {
                        runs.incrementAndGet()
                        release.await()
                        return emptyList()
                    }
                })
            },
            colorScheme = noColors,
            firstRunLimitMs = 10,
        )
        assertEquals(emptyList<Any>(), highlighting.styles)
        val updating = launch(Dispatchers.Default) { highlighting.keepUpdated() }
        release.countDown()
        delay(200)
        updating.cancel()
        assertEquals(1, runs.get())
    }

    @Test
    fun aChangeOfTheTextOnlyWrapsOnce() {
        val state = KodeEditorState("")
        var calls = 0
        val rows = WrappedRows(state) { text, start, end, width ->
            calls++
            cellBreaks(text, start, end, width.toInt())
        }
        state.rowObserver = { rows.onSplice(it, settledWidth = if (state.replacingText) 50f else null) }
        rows.update(100f)
        calls = 0
        val text = List(10) { "word ".repeat(40) }.joinToString("\n")
        state.setText(text)
        rows.update(50f)
        assertEquals(10, calls)
        assertEquals(cellBreaks(text, 0, 200, 50)!!.size + 1, rows.lastRow(0) + 1)
    }
}
