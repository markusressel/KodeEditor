package de.markusressel.kodeeditor.demo

import de.markusressel.kodeeditor.library.compose.rememberLineNumberColumn
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RawRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
import de.markusressel.kodeeditor.library.compose.MarkdownFoldProvider
import de.markusressel.kodeeditor.library.compose.KodeEditorLayout
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KodeEditorTheme {
                val ruleBook = remember { MarkdownRuleBook() }
                val colorScheme = remember { DarkBackgroundColorSchemeWithSpanStyle() }

                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                ) {
                    // not saveable, a large document does not fit into the saved instance state
                    var text by remember {
                        val initialText = readResourceFileAsText(R.raw.short_sample)
                        mutableStateOf(TextFieldValue(initialText))
                    }

                    val columnWidth = with(LocalDensity.current) { 20.dp.roundToPx() }
                    val warnings = remember(columnWidth) { WarningColumn(columnWidth, Color(0xFFFFC107)) }
                    val bookmarks = remember(columnWidth) { BookmarkColumn(columnWidth, Teal200) }

                    LaunchedEffect(text.text) {
                        warnings.warnings = withContext(Dispatchers.Default) { findWarnings(text.text) }
                    }

                    warnings.selectedMessage?.let { message ->
                        Text(
                            modifier = Modifier.padding(4.dp),
                            text = message,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }

                    val editorTextStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                    )

                    KodeEditor(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary)),
                        languageRuleBook = ruleBook,
                        colorScheme = colorScheme,
                        text = text,
                        onValueChange = {
                            if (it.text != text.text) {
                                val edit = LineEdit(text.text, it.text)
                                bookmarks.onEdit(edit)
                                warnings.onEdit(edit)
                            }
                            text = it
                        },
                        textStyle = editorTextStyle,
                        colors = KodeEditorDefaults.editorColors(),
                        lineWrapping = true,
                        foldProvider = MarkdownFoldProvider,
                        gutterColumns = listOf(
                            bookmarks,
                            warnings,
                            rememberLineNumberColumn(editorTextStyle, KodeEditorDefaults.editorColors()),
                        ),
                        overlay = { layout ->
                            // fake cursor and selection of another user
                            val selectedText = "syntax highlighting"
                            val selectionStart = text.text.indexOf(selectedText)
                            // the text can be edited, so the phrase may be gone
                            if (selectionStart >= 0) {
                                RemoteCursor(
                                    layout = layout,
                                    name = "Jane",
                                    color = Teal200,
                                    selection = TextRange(selectionStart, selectionStart + selectedText.length),
                                )
                            }
                        }
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
        layout: KodeEditorLayout,
        name: String,
        color: Color,
        selection: TextRange,
    ) {
        if (selection.min < 0 || selection.max > layout.text.length) {
            return
        }

        val selectionPath = layout.getPathForRange(selection.min, selection.max)
        val cursorRect = layout.getCursorRect(selection.end)

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