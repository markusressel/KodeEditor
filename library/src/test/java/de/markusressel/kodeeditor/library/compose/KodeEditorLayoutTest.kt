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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
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
