package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * @param onValueChange callback for changes to the text and/or cursor selection
 * @param colors the color scheme to use for highlighting
 * @param textStyle the text style used for the editor text
 * @param enabled whether the editor is enabled
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
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }

    Row(modifier = modifier) {
        // Line Numbers
        ZoomLayout(
            modifier = Modifier.zIndex(1f),
            offset = offset.copy(x = 0f),
            zoom = zoom,
            onOffsetChanged = {},
            onZoomChanged = {},
        ) {
            LineNumbers(
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
                    .padding(horizontal = 4.dp),
                value = text,
                languageRuleBook = languageRuleBook,
                colorScheme = colorScheme,
                onValueChange = onValueChange,
                colors = colors.textFieldColors(enabled = enabled).value,
                textStyle = textStyle,
            )
        }
    }
}
