package de.markusressel.kodeeditor.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.ui.KodeTextField
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldColors
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldDefaults

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
                colors = colors,
                enabled = enabled,
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

@Composable
fun LineNumbers(
    text: String,
    colors: KodeTextFieldColors,
    enabled: Boolean,
) {
    val lineNumbers by produceState("") {
        val lineCount = text.lines().size
        value = (1..lineCount).joinToString(separator = "\n")
    }

    Text(
        modifier = Modifier
            .wrapContentWidth()
            .background(color = colors.backgroundColor(enabled = enabled).value)
            .padding(start = 4.dp, end = 4.dp),
        text = lineNumbers,
        textAlign = TextAlign.End,
    )
}

