package de.markusressel.kodeeditor.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.markusressel.kodeeditor.demo.ui.theme.KodeEditorTheme
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.KodeEditorDefaults
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KodeEditorTheme {
                var currentFontSize by remember {
                    mutableStateOf(14)
                }

                Column(
                    modifier = Modifier.background(MaterialTheme.colors.background),
                ) {
                    KodeEditorConfigurationMenu(
                        currentFontSize = currentFontSize,
                        onIncreaseFontSize = { currentFontSize++ },
                        onDecreaseFontSize = { currentFontSize-- },
                    )

                    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        val initialText = readResourceFileAsText(R.raw.short_sample)
//                        val initialText = readResourceFileAsText(R.raw.sample_text)
                        mutableStateOf(TextFieldValue(initialText))
                    }

                    KodeEditor(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(BorderStroke(1.dp, MaterialTheme.colors.primary)),
                        languageRuleBook = MarkdownRuleBook(),
                        colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
                        text = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontSize = currentFontSize.sp).copy(
                            color = MaterialTheme.colors.onSurface,
                        ),
                        colors = KodeEditorDefaults.editorColors()
                    )
                }
            }
        }
    }

    @Composable
    private fun KodeEditorConfigurationMenu(
        currentFontSize: Int,
        onIncreaseFontSize: () -> Unit,
        onDecreaseFontSize: () -> Unit,
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Font Size: $currentFontSize",
                    color = MaterialTheme.colors.onSurface,
                )

                Button(onClick = onIncreaseFontSize) {
                    Text(
                        text = "+",
                        color = MaterialTheme.colors.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.size(4.dp))

                Button(onClick = onDecreaseFontSize) {
                    Text(
                        text = "-",
                        color = MaterialTheme.colors.onPrimary,
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