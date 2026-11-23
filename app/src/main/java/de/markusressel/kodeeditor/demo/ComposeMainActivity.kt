package de.markusressel.kodeeditor.demo

import de.markusressel.kodeeditor.library.compose.rememberLineNumberColumn
import android.content.Intent
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.markusressel.kodeeditor.demo.ui.theme.KodeEditorTheme
import de.markusressel.kodeeditor.demo.ui.theme.Teal200
import de.markusressel.kodeeditor.library.compose.KodeEditor
import de.markusressel.kodeeditor.library.compose.KodeEditorDefaults
import de.markusressel.kodeeditor.library.compose.KodeEditorAnimations
import de.markusressel.kodeeditor.library.compose.KodeEditorAppearance
import de.markusressel.kodeeditor.library.compose.KodeEditorLayout
import de.markusressel.kodeeditor.library.compose.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.kodeeditor.library.compose.TextSplice
import de.markusressel.kodeeditor.library.compose.rememberKodeEditorViewState
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.markdown.colorscheme.DarkBackgroundColorSchemeWithSpanStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeMainActivity : ComponentActivity() {

    /** whether the scenario of the showcase mode plays, see [EXTRA_SHOWCASE_PLAY] */
    private var playShowcase by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOWCASE_PLAY, false)) playShowcase = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // plays the scenario of the README animation, without the controls of the demo
        val showcase = intent.getBooleanExtra(EXTRA_SHOWCASE, false)
        if (showcase) {
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
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
                    var readOnly by remember { mutableStateOf(showcase) }
                    val viewState = rememberKodeEditorViewState()

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
                    var appearOn by remember { mutableStateOf(true) }
                    var appearMs by remember { mutableIntStateOf(250) }
                    var appearWindow by remember { mutableIntStateOf(16) }
                    var appearSlide by remember { mutableIntStateOf(if (showcase) SHOWCASE_SLIDE_DP else 0) }
                    var showOptions by remember { mutableStateOf(false) }
                    var editorLayout by remember { mutableStateOf<KodeEditorLayout?>(null) }
                    val scope = rememberCoroutineScope()
                    var janeTyping by remember { mutableStateOf<Job?>(null) }
                    // how much Jane typed behind her phrase, null while she only has it selected
                    var janeTyped by remember { mutableStateOf<Int?>(null) }

                    // not the head itself, that would compose all of this again for each step of the typing
                    val agentTyping by remember(agent) { derivedStateOf { agent.head != null } }
                    LaunchedEffect(agentTyping) {
                        while (agentTyping && agent.reveal(revealSteps) { editorLayout?.collapsedRangeAt(it) }) {
                            delay(REVEAL_INTERVAL_MS)
                        }
                    }

                    if (showcase) {
                        LaunchedEffect(Unit) {
                            snapshotFlow { playShowcase }.first { it }
                            playShowcase(state, viewState, agent) { janeTypes(state) { janeTyped = it } }
                        }
                    }

                    if (!showcase) Row(verticalAlignment = Alignment.CenterVertically) {
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
                        TextButton(
                            enabled = janeTyping == null,
                            onClick = {
                                janeTyping = scope.launch {
                                    janeTypes(state) { janeTyped = it }
                                    janeTyping = null
                                }
                            },
                        ) {
                            Text("Jane types")
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
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Appear animation", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                            Switch(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                checked = appearOn,
                                onCheckedChange = { appearOn = it },
                            )
                        }
                        OptionSlider("Appear duration (ms)", appearMs, 0..1000) { appearMs = it }
                        OptionSlider("Appearing characters", appearWindow, 1..60) { appearWindow = it }
                        OptionSlider("Slide (dp)", appearSlide, 0..20) { appearSlide = it }
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
                        viewState = viewState,
                        // only what the agent and Jane write appears animated, typed text is there at once
                        animations = KodeEditorAnimations(
                            appearance = if (appearOn) {
                                KodeEditorAppearance(
                                    durationMs = appearMs,
                                    window = appearWindow,
                                    slide = appearSlide.dp,
                                )
                            } else {
                                null
                            },
                        ),
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
                            val selectedText = JANE_PHRASE
                            val selectionStart = text.indexOf(selectedText)
                            // the text can be edited, so the phrase may be gone
                            if (selectionStart >= 0) {
                                val phraseEnd = selectionStart + selectedText.length
                                val typed = janeTyped
                                RemoteCursor(
                                    layout = layout,
                                    name = "Jane",
                                    color = Teal200,
                                    // her cursor follows what she types
                                    selection = if (typed == null) {
                                        TextRange(selectionStart, phraseEnd)
                                    } else {
                                        TextRange(phraseEnd + typed)
                                    },
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

    /** another person, who types a line behind the phrase "Jane" selected, as remote changes that appear animated */
    /** @param onTyped told how many characters Jane typed so far */
    private suspend fun janeTypes(state: KodeEditorState, onTyped: (Int) -> Unit) {
        JANE_TEXT.forEachIndexed { typed, char ->
            delay(JANE_INTERVAL_MS)
            // found again each time, the user may have changed the text in front of it meanwhile
            val anchor = state.text.indexOf(JANE_PHRASE).takeIf { it >= 0 } ?: return
            val position = anchor + JANE_PHRASE.length + typed
            state.applyRemote(listOf(TextSplice(position, 0, char.toString())), appear = true, appearTag = "jane")
            onTyped(typed + 1)
        }
    }

    private fun readResourceFileAsText(@RawRes resourceId: Int) =
        resources.openRawResource(resourceId).bufferedReader().readText()

}


/** how often the agent of the demo types, about 30 times per second */
private const val REVEAL_INTERVAL_MS = 33L

/** what Jane has selected, and types behind */
private const val JANE_PHRASE = "syntax highlighting"
private const val JANE_TEXT = " (Jane: this line appears as I type it)"
private const val JANE_INTERVAL_MS = 90L

/** how far the text of the agent and Jane rises into its place in the showcase */
private const val SHOWCASE_SLIDE_DP = 4

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