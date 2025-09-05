package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import de.markusressel.kodehighlighter.core.ui.KodeTextField

/**
 * Compose version of the KodeEditorLayout
 *
 * @param modifier compose modifiers
 * @param text the current text of the editor
 * @param languageRuleBook the language rule book to use for highlighting
 * @param colorScheme the color scheme to apply
 * @param onValueChange callback for changes to the text and/or cursor selection
 * @param colors the color scheme to use for highlighting
 * @param textStyle the text style used for the editor text
 * @param enabled whether the editor is enabled
 * @param readOnly whether the contents of the editor can be changed by the user
 */
@Composable
fun KodeEditor(
    modifier: Modifier = Modifier,
    text: TextFieldValue,
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
    onValueChange: (TextFieldValue) -> Unit,
    colors: KodeEditorColors = KodeEditorDefaults.editorColors(),
    textStyle: TextStyle = LocalTextStyle.current,
    enabled: Boolean = true,
    readOnly: Boolean = enabled.not(),
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }

    Box(modifier = Modifier
        .clipToBounds()
        .then(modifier)) {
        var lineNumberWidth by remember {
            mutableIntStateOf(0)
        }
        LineNumbers(
            modifier = Modifier
                .zIndex(1f)
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                .onGloballyPositioned {
                    lineNumberWidth = it.size.width
                }
                .graphicsLayer(
                    transformOrigin = TransformOrigin(0f, 0f),
                    scaleX = zoom, scaleY = zoom,
                    translationX = 0f,
                    translationY = -offset.y * zoom,
                ),
            text = text.text,
            textStyle = textStyle,
            textColor = colors.lineNumberTextColor().value,
            backgroundColor = colors.lineNumberBackgroundColor().value,
        )

        val computedPadding = LocalDensity.current.run {
            (lineNumberWidth).coerceAtLeast(0).toDp()
        }

        // Text Editor
        var totalSize by remember { mutableStateOf(IntSize.Zero) }
        var maxXOffset by remember(totalSize) { mutableFloatStateOf(Float.MAX_VALUE) }
        var maxYOffset by remember(totalSize) { mutableFloatStateOf(Float.MAX_VALUE) }

        ZoomLayout(
            modifier = Modifier
                .zIndex(0f)
                .padding(
                    start = computedPadding,
                )
                .matchParentSize()
                .onSizeChanged { size ->
                    totalSize = size
                },
            offset = offset,
            zoom = zoom,
            onOffsetChanged = {
                val newOffset = offset + (it / zoom)
                offset = newOffset.copy(
                    x = newOffset.x.coerceIn(0f, maxXOffset),
                    y = newOffset.y.coerceIn(0f, maxYOffset)
                )
            },
            onZoomChanged = {
                zoom *= it
            },
        ) {
            KodeTextField(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .matchParentSize()
                    .background(colors.textFieldBackgroundColor().value)
                    .padding(start = 4.dp, end = 4.dp)
                    .onSizeChanged { unboundedSize ->
                        maxXOffset = (unboundedSize.width - totalSize.width).toFloat()
                        maxYOffset = (unboundedSize.height - totalSize.height).toFloat()
                    },
                value = text,
                languageRuleBook = languageRuleBook,
                colorScheme = colorScheme,
                onValueChange = onValueChange,
                colors = colors.textFieldColors(enabled = enabled).value,
                textStyle = textStyle,
                enabled = enabled,
                readOnly = readOnly,
            )
        }
    }
}


private data class DummyData(
    val headingRule: LanguageRule = object : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> {
            val PATTERN = "^\\s{0,3}#{1,6} .+".toRegex(RegexOption.MULTILINE)
            return RuleHelper.findRegexMatches(text, PATTERN)
        }
    },

    val dummyRuleBook: LanguageRuleBook = object : LanguageRuleBook {
        override fun getRules() = listOf(headingRule)
    },

    val colorScheme: ColorScheme<SpanStyle> = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule): Set<StyleFactory<SpanStyle>> {
            return setOf { SpanStyle(Color(0xFFFF6D00)) }
        }
    }
)

private val dummyData = DummyData()

@Preview
@Composable
private fun KodeEditorPreview() {
    var text by remember {
        val initialText = """
            # Hello World
            Code: `readResourceFileAsText(R.raw.sample_text)`
            
            ## Secondary headline
            
            This is a listing:
            
            * 1
            * 2
            * 3
            
            # Code Block
            
            ```
            This is a code block.
            ```
        """.trimIndent()
        mutableStateOf(TextFieldValue(text = initialText))
    }

    val languageRuleBook by remember {
        mutableStateOf(dummyData.dummyRuleBook)
    }
    val colorScheme by remember {
        mutableStateOf(dummyData.colorScheme)
    }

    KodeEditor(
        modifier = Modifier.fillMaxSize(),
        text = text,
        languageRuleBook = languageRuleBook,
        colorScheme = colorScheme,
        onValueChange = { text = it }
    )
}
