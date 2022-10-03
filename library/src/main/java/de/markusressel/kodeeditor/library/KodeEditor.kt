package de.markusressel.kodeeditor.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import kotlinx.coroutines.runBlocking

@Composable
fun KodeEditor(
        modifier: Modifier = Modifier,
        initialText: String = "",
        languageRuleBook: LanguageRuleBook,
        colorScheme: ColorScheme = languageRuleBook.defaultColorScheme
) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText))
    }

    val visualTransformation = HighlightingTransformation(languageRuleBook, colorScheme)

    Scaffold(
            modifier = Modifier.background(Color.Black),
            backgroundColor = Color.Black
    ) { contentPadding ->

        ZoomLayout {
            BasicTextField(
                modifier = Modifier
                    .padding(contentPadding)
                    .wrapContentSize(
                        align = Alignment.TopStart,
                        unbounded = true
                    )
                    .background(Color.White),
                value = text,
                onValueChange = { text = it },
                visualTransformation = visualTransformation,
            )
        }
    }
}

class HighlightingTransformation(
        ruleBook: LanguageRuleBook,
        colorScheme: ColorScheme
) : VisualTransformation {

    private val highlighter = AnnotatedStringHighlighter(ruleBook, colorScheme)

    override fun filter(text: AnnotatedString): TransformedText {
        // TODO: run asynchronously
        val highlightedText = runBlocking {
            highlighter.highlight(text.text)
        }
        return TransformedText(highlightedText, OffsetMapping.Identity)
    }
}
