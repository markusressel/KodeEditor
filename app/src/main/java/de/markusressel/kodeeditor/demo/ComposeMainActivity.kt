package de.markusressel.kodeeditor.demo

import de.markusressel.kodeeditor.library.compose.rememberLineNumberColumn
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import de.markusressel.kodeeditor.library.compose.KodeEditorLayout
import de.markusressel.kodeeditor.library.compose.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.KodeEditorState
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
                    val state = remember { KodeEditorState(readResourceFileAsText(R.raw.short_sample)) }
                    val agent = remember(state) { AgentTyping(state) }
                    var readOnly by remember { mutableStateOf(false) }

                    val columnWidth = with(LocalDensity.current) { 20.dp.roundToPx() }
                    val warnings = remember(columnWidth) { WarningColumn(columnWidth, Color(0xFFFFC107)) }
                    val bookmarks = remember(columnWidth) { BookmarkColumn(columnWidth, Teal200) }

                    DisposableEffect(state, warnings, bookmarks) {
                        var lastText = state.text
                        val lines = object : KodeEditorListener {
                            // remote changes of the agent move the lines too, so this does not use onLocalEdit
                            override fun onStateChanged() {
                                if (state.text == lastText) return
                                val edit = LineEdit(lastText, state.text)
                                bookmarks.onEdit(edit)
                                warnings.onEdit(edit)
                                lastText = state.text
                            }
                        }
                        state.addListener(lines)
                        state.addListener(agent)
                        onDispose {
                            state.removeListener(lines)
                            state.removeListener(agent)
                        }
                    }

                    val text = state.text
                    LaunchedEffect(text) {
                        warnings.warnings = withContext(Dispatchers.Default) { findWarnings(text) }
                    }

                    var revealSteps by remember { mutableIntStateOf(2) }
                    var fadeMs by remember { mutableIntStateOf(250) }
                    var fadeWindow by remember { mutableIntStateOf(16) }
                    var showOptions by remember { mutableStateOf(false) }
                    var editorLayout by remember { mutableStateOf<KodeEditorLayout?>(null) }

                    // not the head itself, that would compose all of this again for each frame of the typing
                    val agentTyping by remember(agent) { derivedStateOf { agent.head != null } }
                    val agentActive by remember(agent) { derivedStateOf { agent.head != null || agent.fading } }
                    LaunchedEffect(agentActive) {
                        // no fading when the system turns animations off
                        val scale = Settings.Global.getFloat(
                            contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
                        )
                        agent.fadeMs = (fadeMs * scale).toInt()
                        agent.fadeWindow = fadeWindow
                        var lastReveal = 0L
                        while (agentActive) {
                            val active = withFrameMillis { now ->
                                // about 30 steps per second, the fading runs with every frame
                                if (now - lastReveal >= REVEAL_INTERVAL_MS) {
                                    lastReveal = now
                                    agent.reveal(revealSteps, now) { editorLayout?.collapsedRangeAt(it) }
                                }
                                agent.fade(now)
                                agent.head != null || agent.fading
                            }
                            if (!active) break
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                if (agentTyping) {
                                    agent.stop()
                                } else {
                                    // at the start of the line of the cursor, the text of that line moves down
                                    val cursor = state.selection.min
                                    agent.start(AGENT_TEXT, state.text.lastIndexOf('\n', cursor - 1) + 1)
                                }
                            },
                        ) {
                            Text(if (agentTyping) "Stop agent" else "Agent types")
                        }
                        TextButton(onClick = { showOptions = !showOptions }) {
                            Text(if (showOptions) "Hide options" else "Options")
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Read only", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            checked = readOnly,
                            onCheckedChange = { readOnly = it },
                        )
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

                    if (showOptions) {
                        OptionSlider("Characters per step", revealSteps, 1..10) { revealSteps = it }
                        OptionSlider("Fade in (ms)", fadeMs, 0..1000) { fadeMs = it }
                        OptionSlider("Fading characters at most", fadeWindow, 1..60) { fadeWindow = it }
                    }

                    KodeEditor(
                        onTextLayout = { editorLayout = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary)),
                        languageRuleBook = ruleBook,
                        colorScheme = colorScheme,
                        state = state,
                        readOnly = readOnly,
                        textStyle = editorTextStyle,
                        colors = KodeEditorDefaults.editorColors(),
                        lineWrapping = true,
                        lineDecorations = listOf(warnings),
                        gutterColumns = listOf(
                            bookmarks,
                            warnings,
                            rememberLineNumberColumn(editorTextStyle, KodeEditorDefaults.editorColors()),
                        ),
                        overlay = { layout ->
                            // fake cursor and selection of another user
                            val selectedText = "syntax highlighting"
                            val selectionStart = text.indexOf(selectedText)
                            // the text can be edited, so the phrase may be gone
                            if (selectionStart >= 0) {
                                RemoteCursor(
                                    layout = layout,
                                    name = "Jane",
                                    color = Teal200,
                                    selection = TextRange(selectionStart, selectionStart + selectedText.length),
                                )
                            }
                            agent.head?.let { head ->
                                RemoteCursor(
                                    layout = layout,
                                    name = "Agent",
                                    color = Color(0xFFFF4081),
                                    selection = TextRange(head),
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

    @Composable
    private fun OptionSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.width(200.dp),
                text = "$label: $value",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = value.toFloat(),
                valueRange = range.first.toFloat()..range.last.toFloat(),
                onValueChange = { onChange(it.toInt()) },
            )
        }
    }

    private fun readResourceFileAsText(@RawRes resourceId: Int) =
        resources.openRawResource(resourceId).bufferedReader().readText()

}


/** how often the agent of the demo types, about 30 times per second */
private const val REVEAL_INTERVAL_MS = 33L

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