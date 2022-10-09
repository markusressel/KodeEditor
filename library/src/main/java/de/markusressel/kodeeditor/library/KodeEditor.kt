package de.markusressel.kodeeditor.library

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
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
) {
    ZoomLayout(modifier = modifier) {
        KodeTextField(
            modifier = Modifier
                .wrapContentSize(
                    align = Alignment.TopStart,
                    unbounded = true
                ),
            value = text,
            languageRuleBook = languageRuleBook,
            colorScheme = colorScheme,
            onValueChange = onValueChange,
            colors = colors,
        )
    }
}
