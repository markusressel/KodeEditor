package de.markusressel.kodeeditor.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.ui.KodeTextField

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
) {
    Scaffold(
        modifier = Modifier
            .background(Color.Black)
            .then(modifier),
        backgroundColor = Color.Black
    ) { contentPadding ->
        ZoomLayout {
            KodeTextField(
                modifier = Modifier
                    .padding(contentPadding)
                    .wrapContentSize(
                        align = Alignment.TopStart,
                        unbounded = true
                    )
                    .background(Color.White),
                value = text,
                languageRuleBook = languageRuleBook,
                colorScheme = colorScheme,
                onValueChange = onValueChange,
            )
        }
    }
}
