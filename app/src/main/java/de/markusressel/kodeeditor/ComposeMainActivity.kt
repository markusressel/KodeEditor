package de.markusressel.kodeeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.KodeEditorDefaults
import de.markusressel.kodeeditor.ui.theme.KodeEditorTheme
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KodeEditorTheme {
                var fontSize by remember {
                    mutableStateOf(14)
                }
                Column {
                    Row {
                        Button(onClick = { fontSize++ }) {
                            Text(text = "+")
                        }

                        Button(onClick = { fontSize-- }) {
                            Text(text = "-")
                        }
                    }

                    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        val initialText = readResourceFileAsText(R.raw.sample_text)
                        mutableStateOf(TextFieldValue(initialText))
                    }

                    KodeEditor(
                        modifier = Modifier.fillMaxSize(),
                        languageRuleBook = MarkdownRuleBook(),
                        colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
                        text = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontSize = fontSize.sp),
                        colors = KodeEditorDefaults.editorColors(
                            lineNumberTextColor = Color.Black,
                            lineNumberBackgroundColor = Color.White,
                        )
                    )
                }
            }
        }
    }

    private fun readResourceFileAsText(@RawRes resourceId: Int) =
        resources.openRawResource(resourceId).bufferedReader().readText()

}


@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    KodeEditorTheme {
        var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            val initialText = """
                # Heading
                    
                Heading Test
                    
                ## Subheading
                    
                Subheading Text
                """.trimIndent()
            mutableStateOf(TextFieldValue(initialText))
        }

        KodeEditor(
            languageRuleBook = MarkdownRuleBook(),
            colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
            text = text,
            onValueChange = { text = it }
        )
    }
}