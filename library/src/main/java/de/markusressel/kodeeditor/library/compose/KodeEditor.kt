package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import de.markusressel.kodehighlighter.core.ui.KodeTextField

data class DummyData(
    val headingRule: LanguageRule = object : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> {
            val PATTERN = "^\\s{0,3}#{1,6} .+".toRegex(RegexOption.MULTILINE)
            return RuleHelper.findRegexMatches(text, PATTERN)
        }
    },

    val dummyRuleBook: LanguageRuleBook = object : LanguageRuleBook {
        override fun getRules() = listOf(
            headingRule
        )
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
        """.trimIndent()
        mutableStateOf(TextFieldValue(
            text = initialText
        ))
    }

    val languageRuleBook by remember {
        mutableStateOf(dummyData.dummyRuleBook)
    }
    val colorScheme by remember {
        mutableStateOf(dummyData.colorScheme)
    }

    KodeEditor(
        text = text,
        languageRuleBook = languageRuleBook,
        colorScheme = colorScheme,
        onValueChange = { text = it }
    )
}

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
    var zoom by remember { mutableStateOf(1f) }

    Row(modifier = Modifier
        .clipToBounds()
        .then(modifier)
    ) {
        var lineNumberWidth by remember {
            mutableStateOf(0)
        }

        // Line Numbers
        Box(
            modifier = Modifier.zIndex(1f),
        ) {
            LineNumbers(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .wrapContentWidth()
                    .wrapContentSize(
                        align = Alignment.TopStart,
                        unbounded = true
                    )
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
        }

//        val configuration = LocalConfiguration.current

        // Text Editor
        ZoomLayout(
            modifier = Modifier.zIndex(0f),
            offset = offset,
            zoom = zoom,
            onOffsetChanged = {
//                val newOffset = Offset(
//                    x = (offset + it).x.coerceIn(0f, (size.width.toFloat() - (configuration.screenWidthDp.dp.toPx() / newScale)).coerceAtLeast(0f)),
//                    y = (offset + it).y.coerceIn(0f, (size.height.toFloat() - (configuration.screenHeightDp.dp.toPx() / newScale)).coerceAtLeast(0f)),
//                )

                val newOffset = offset + (it / zoom)
                offset = newOffset.copy(
                    x = newOffset.x.coerceAtLeast(0f),
                    y = newOffset.y.coerceAtLeast(0f)
                )
            },
            onZoomChanged = { zoom *= it },
        ) {
            KodeTextField(
                modifier = Modifier
                    .wrapContentSize(
                        align = Alignment.TopStart,
                        unbounded = true
                    )
                    .background(colors.textFieldBackgroundColor().value)
                    .padding(
                        start = LocalDensity.current.run { lineNumberWidth.toDp() } + 4.dp,
                        end = 4.dp
                    ),
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
