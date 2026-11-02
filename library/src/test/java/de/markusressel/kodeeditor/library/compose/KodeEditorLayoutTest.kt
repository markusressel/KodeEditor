package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class KodeEditorLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var layout: TextLayoutResult? = null

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private fun show(text: String, overlay: @Composable BoxScope.(TextLayoutResult) -> Unit = {}) {
        val value = mutableStateOf(TextFieldValue(text))
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp),
                text = value.value,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onValueChange = { value.value = it },
                onTextLayout = { layout = it },
                overlay = overlay,
            )
        }
        composeRule.waitForIdle()
        assertNotNull(layout)
    }

    @Test
    fun `a document with 10000 lines can be laid out`() {
        show((1..10_000).joinToString("\n") { "line number $it with some text on it" })
    }

    @Test
    fun `each line gets its text and the part of the styles within it`() {
        val style = SpanStyle(color = Color.Red)
        val text = AnnotatedString("one\n\ntwo", listOf(AnnotatedString.Range(style, 1, 6)))
        val result = lineTexts(styledLayout(text), 0..2)
        assertEquals(listOf("one", "", "two"), result.map { it.text })
        assertEquals(listOf(AnnotatedString.Range(style, 1, 3)), result[0].spanStyles)
        assertEquals(emptyList<AnnotatedString.Range<SpanStyle>>(), result[1].spanStyles)
        assertEquals(listOf(AnnotatedString.Range(style, 0, 1)), result[2].spanStyles)
    }

    @Test
    fun `the highlighting only runs when the text changes`() {
        var runs = 0
        val countingRules = object : LanguageRuleBook {
            private val rule = object : LanguageRule {
                override fun findMatches(text: CharSequence): List<RuleMatch> {
                    runs++
                    return emptyList()
                }
            }

            override fun getRules() = listOf<LanguageRule>(rule)
        }
        var value by mutableStateOf(TextFieldValue("some text"))
        composeRule.setContent {
            KodeEditor(
                modifier = Modifier.size(400.dp),
                text = value,
                languageRuleBook = countingRules,
                colorScheme = noColors,
                onValueChange = { value = it },
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, runs)

        value = value.copy(selection = TextRange(2))
        composeRule.waitForIdle()
        assertEquals(1, runs)

        value = value.copy(text = "some more text")
        composeRule.waitForIdle()
        assertEquals(2, runs)
    }

    @Test
    fun `the text layout only gets the styles that change the size of the text`() {
        val color = SpanStyle(color = Color.Red, textDecoration = TextDecoration.Underline)
        val bold = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)
        assertFalse(color.changesTextSize())
        assertTrue(bold.changesTextSize())

        val rule = object : LanguageRule {
            override fun findMatches(text: CharSequence) = listOf(RuleMatch(0, 3))
        }
        val boldRule = object : LanguageRule {
            override fun findMatches(text: CharSequence) = listOf(RuleMatch(4, 7))
        }
        val highlighting = CachedHighlighting(
            object : LanguageRuleBook {
                override fun getRules() = listOf(rule, boldRule)
            },
            object : ColorScheme<SpanStyle> {
                override fun getStyles(type: LanguageRule): Set<StyleFactory<SpanStyle>> =
                    setOf(if (type === rule) ({ color }) else ({ bold }))
            },
        )
        val forLayout = highlighting.filter(AnnotatedString("one two")).text
        assertEquals(listOf(bold), forLayout.spanStyles.map { it.item })
        assertEquals(2, highlighting.stylesOf("one two").size)
        assertEquals(emptyList<Any>(), highlighting.stylesOf("something else"))
    }

    private fun styledLayout(text: AnnotatedString): TextLayoutResult {
        lateinit var result: TextLayoutResult
        composeRule.runOnUiThread {
            result = TextMeasurer(
                defaultFontFamilyResolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext()),
                defaultDensity = composeRule.density,
                defaultLayoutDirection = LayoutDirection.Ltr,
            ).measure(text, softWrap = false)
        }
        return result
    }

    /** neither direction alone is too large here, only the combination can not be represented in constraints */
    @Test
    fun `a long line next to many lines can be laid out`() {
        show("x".repeat(40_000) + "\n" + (1..2_000).joinToString("\n") { "line $it" })
    }

    @Test
    fun `the overlay is measured at the size of the editor, not of the document`() {
        var overlaySize = IntSize.Zero
        show((1..10_000).joinToString("\n") { "line $it" }) {
            Box(Modifier.matchParentSize().onSizeChanged { overlaySize = it })
        }
        composeRule.waitForIdle()
        val editorHeight = composeRule.density.run { 400.dp.roundToPx() }
        assertTrue("overlay has size $overlaySize", overlaySize.height in 1..editorHeight)
    }

    @Test
    fun `a pinch stops at the zoom limits`() {
        val near = Offset(20f, 0f)
        val far = Offset(150f, 0f)
        var zoom by mutableFloatStateOf(1f)
        var offset by mutableStateOf(Offset.Zero)
        var smallest = zoom
        var largest = zoom
        composeRule.setContent {
            ZoomLayout(
                modifier = Modifier.size(400.dp).testTag("zoom"),
                zoom = zoom,
                minZoom = 0.25f,
                maxZoom = 2f,
                offset = offset,
                onOffsetChanged = { offset = it },
                onZoomChanged = {
                    zoom = it
                    smallest = minOf(smallest, it)
                    largest = maxOf(largest, it)
                },
            ) {}
        }
        val spread: TouchInjectionScope.() -> Unit = {
            pinch(center - near, center - far, center + near, center + far)
        }
        val squeeze: TouchInjectionScope.() -> Unit = {
            pinch(center - far, center - near, center + far, center + near)
        }
        composeRule.onNodeWithTag("zoom").performTouchInput(spread)
        composeRule.waitForIdle()
        assertEquals(2f, largest)
        assertEquals(2f, zoom)

        composeRule.onNodeWithTag("zoom").performTouchInput(squeeze)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("zoom").performTouchInput(squeeze)
        composeRule.waitForIdle()
        assertEquals(0.25f, smallest)
        assertEquals(0.25f, zoom)
    }
}
