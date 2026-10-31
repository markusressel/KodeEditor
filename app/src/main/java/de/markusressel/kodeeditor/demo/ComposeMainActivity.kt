package de.markusressel.kodeeditor.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.markusressel.kodeeditor.demo.ui.theme.KodeEditorTheme
import de.markusressel.kodeeditor.demo.ui.theme.Teal200
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
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
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
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary)),
                        languageRuleBook = MarkdownRuleBook(),
                        colorScheme = DarkBackgroundColorSchemeWithSpanStyle(),
                        text = text,
                        onValueChange = { text = it },
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = currentFontSize.sp,
                            lineHeight = currentFontSize.sp
                        ),
                        colors = KodeEditorDefaults.editorColors(),
                        overlay = { textLayoutResult ->
                            // fake cursor and selection of another user
                            val selectedText = "syntax highlighting"
                            val selectionStart = text.text.indexOf(selectedText)
                            RemoteCursor(
                                textLayoutResult = textLayoutResult,
                                name = "Jane",
                                color = Teal200,
                                selection = TextRange(selectionStart, selectionStart + selectedText.length),
                            )
                        }
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
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Button(onClick = onIncreaseFontSize) {
                    Text(
                        text = "+",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.size(4.dp))

                Button(onClick = onDecreaseFontSize) {
                    Text(
                        text = "-",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    /**
     * Draws the cursor, selection and name of another user on top of the editor text
     */
    @Composable
    private fun BoxScope.RemoteCursor(
        textLayoutResult: TextLayoutResult,
        name: String,
        color: Color,
        selection: TextRange,
    ) {
        if (selection.min < 0 || selection.max > textLayoutResult.layoutInput.text.length) {
            return
        }

        val selectionPath = textLayoutResult.getPathForRange(selection.min, selection.max)
        val cursorRect = textLayoutResult.getCursorRect(selection.end)

        Canvas(modifier = Modifier.matchParentSize()) {
            drawPath(path = selectionPath, color = color.copy(alpha = 0.3f))
            drawLine(
                color = color,
                start = cursorRect.topCenter,
                end = cursorRect.bottomCenter,
                strokeWidth = 2.dp.toPx(),
            )
        }

        Text(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.place(
                            x = cursorRect.left.toInt(),
                            y = cursorRect.top.toInt() - placeable.height,
                        )
                    }
                }
                .background(color)
                .padding(horizontal = 2.dp),
            text = name,
            color = Color.Black,
            fontSize = 10.sp,
        )
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