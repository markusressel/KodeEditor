package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.ui.KodeTextField
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldColors
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldDefaults
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle

@Preview
@Composable
fun KodeEditorPreview() {
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
        mutableStateOf(MarkdownRuleBook())
    }
    val colorScheme by remember {
        mutableStateOf(DarkBackgroundColorSchemeWithSpanStyle())
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
 */
@Composable
fun KodeEditor(
    modifier: Modifier = Modifier,
    text: TextFieldValue,
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
    onValueChange: (TextFieldValue) -> Unit,
    colors: KodeTextFieldColors = KodeTextFieldDefaults.textFieldColors(),
    enabled: Boolean = true,
) {
    ZoomLayout(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            LineNumbers(
                text = text.text,
                textColor = Color.White,
                backgroundColor = colors.backgroundColor(enabled = enabled).value,
            )

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
                colors = colors,
            )
        }
    }
}
