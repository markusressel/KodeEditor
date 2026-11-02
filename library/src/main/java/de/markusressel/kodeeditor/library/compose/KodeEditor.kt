package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val KEYBOARD_ZOOM_STEP = 1.1f
private const val CURSOR_BLINK_MS = 500L
private val CURSOR_WIDTH = 2.dp

/**
 * Compose version of the KodeEditorLayout
 *
 * @param modifier compose modifiers
 * @param text the current text of the editor
 * @param languageRuleBook the language rule book to use for highlighting, remember it so the highlighting of an
 * unchanged text can be reused
 * @param colorScheme the color scheme to apply, remember it for the same reason
 * @param onValueChange callback for changes to the text and/or cursor selection
 * @param colors the color scheme to use for highlighting
 * @param textStyle the text style used for the editor text
 * @param enabled whether the editor is enabled
 * @param readOnly whether the contents of the editor can be changed by the user
 * @param onTextLayout callback that is executed when a new text layout of the editor text is calculated
 * @param overlay content drawn on top of the editor text, e.g. the cursors and selections of other users.
 * It is placed inside the zoom and scroll transformation and uses the coordinate system of the given
 * [TextLayoutResult], so [TextLayoutResult.getCursorRect] and [TextLayoutResult.getPathForRange] can be used
 * as is. Indices must be within the laid out text, and the content should not consume pointer input.
 * Its box has the size of the editor, not of the document, which can be too large to measure anything at.
 * Content is not clipped to that box, so draw and place it at text coordinates anywhere in the document.
 * @param keepCursorVisible while the editor has the focus, scroll so the cursor stays visible when it moves or
 * the editor gets smaller (e.g. when the soft keyboard opens and the app shrinks the editor by the IME insets)
 * @param minZoom the smallest zoom factor the user can reach
 * @param maxZoom the largest zoom factor the user can reach
 */
@Composable
fun KodeEditor(
    modifier: Modifier = Modifier,
    text: TextFieldValue,
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
    onValueChange: (TextFieldValue) -> Unit,
    colors: KodeEditorColors = KodeEditorDefaults.editorColors(),
    textStyle: TextStyle = LocalTextStyle.current,
    enabled: Boolean = true,
    readOnly: Boolean = enabled.not(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    overlay: @Composable BoxScope.(textLayoutResult: TextLayoutResult) -> Unit = {},
    keepCursorVisible: Boolean = true,
    minZoom: Float = 0.25f,
    maxZoom: Float = 10f,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f.coerceIn(minZoom, maxZoom)) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    val cursorFollower = remember { CursorFollower() }
    val keyboardCursor = remember { KeyboardCursor() }
    val highlighting = remember(languageRuleBook, colorScheme) { CachedHighlighting(languageRuleBook, colorScheme) }

    LaunchedEffect(minZoom, maxZoom) {
        zoom = zoom.coerceIn(minZoom, maxZoom)
    }

    Box(modifier = Modifier
        .clipToBounds()
        .then(modifier)
        // the text only covers the document, which is smaller than the editor for a short text or a low zoom
        .background(colors.textFieldBackgroundColor().value)) {
        val fieldColors = colors.textFieldColors(enabled = enabled).value
        val fieldTextColor = fieldColors.textColor(enabled).value
        val fieldTextStyle = textStyle.copy(color = textStyle.color.takeOrElse { fieldTextColor })
        val lineNumberStyle = fieldTextStyle.copy(
            color = colors.lineNumberTextColor().value.takeOrElse { fieldTextStyle.color },
        )
        val lineCount = remember(text.text) { text.text.count { it == '\n' } + 1 }
        val lineNumberWidth by rememberUpdatedState(rememberLineNumbersWidth(lineCount, lineNumberStyle))

        val computedPadding = LocalDensity.current.run {
            lineNumberWidth.toDp()
        }

        // Text Editor
        var totalSize by remember { mutableStateOf(IntSize.Zero) }
        val textStartPadding = LocalDensity.current.run { 4.dp.toPx() }
        val viewport by remember {
            derivedStateOf { EditorViewport(totalSize, lineNumberWidth, zoom) }
        }
        val maxOffset by remember(textStartPadding) {
            derivedStateOf {
                viewport.maxOffset(textLayoutResult?.size?.let { size ->
                    IntSize(size.width + 2 * textStartPadding.roundToInt(), size.height)
                } ?: IntSize.Zero)
            }
        }

        // the text can get shorter and the zoom can change, the view must not stay beyond the end of the text
        LaunchedEffect(maxOffset) {
            offset = EditorViewport.coerce(offset, maxOffset)
        }

        val drawWindow = remember(textStartPadding) {
            derivedStateOf {
                DrawWindow.around(Offset(offset.x - textStartPadding, offset.y), viewport.visibleSize)
            }
        }

        LineNumbers(
            modifier = Modifier
                .zIndex(1f)
                .matchParentSize(),
            layout = textLayoutResult,
            width = lineNumberWidth,
            textStyle = lineNumberStyle,
            backgroundColor = colors.lineNumberBackgroundColor().value,
            window = drawWindow,
            zoom = { zoom },
            offsetY = { offset.y },
        )

        // Follow the cursor while the user edits: when the selection moves or the editor changes its size.
        // Panning or remote changes of the text alone don't scroll.
        LaunchedEffect(keepCursorVisible, focused, text.selection, totalSize, zoom) {
            val follow = cursorFollower.shouldFollow(text.selection, focused)
            if (!keepCursorVisible || !follow || totalSize == IntSize.Zero) return@LaunchedEffect
            // wait for the layout of the current text, the selection may refer to text that is not laid out yet
            val layout = snapshotFlow { textLayoutResult }
                .first { it != null && it.layoutInput.text.text == text.text } ?: return@LaunchedEffect
            val cursor = layout.getCursorRect(text.selection.end.coerceIn(0, layout.layoutInput.text.length))
            offset = CursorVisibility.offsetFor(
                offset = offset,
                cursor = cursor.translate(textStartPadding, 0f),
                viewport = viewport.visibleSize,
                maxOffset = maxOffset,
            )
        }

        ZoomLayout(
            modifier = Modifier
                .zIndex(0f)
                .padding(
                    start = computedPadding,
                )
                .matchParentSize()
                .onSizeChanged { size ->
                    totalSize = size
                },
            offset = offset,
            zoom = zoom,
            minZoom = minZoom,
            maxZoom = maxZoom,
            onOffsetChanged = {
                cursorFollower.onUserMovedView()
                offset = EditorViewport.coerce(it, maxOffset)
            },
            onZoomChanged = {
                cursorFollower.onUserMovedView()
                zoom = it
            },
        ) {
            BasicTextField(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    // goes before the text field, which would move the focus on the arrow keys
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !event.isFromTextKeyboard()) {
                            return@onPreviewKeyEvent false
                        }
                        when (val command = event.toKeyboardCommand()) {
                            null -> false

                            is KeyboardCommand.Move -> {
                                val layout = textLayoutResult ?: return@onPreviewKeyEvent false
                                val selection = keyboardCursor.move(
                                    text = text.text,
                                    selection = text.selection,
                                    move = command.move,
                                    extend = event.isShiftPressed,
                                    lines = LayoutTextLines(layout),
                                    pageHeight = totalSize.height / zoom,
                                )
                                if (selection != text.selection) {
                                    onValueChange(text.copy(selection = selection, composition = null))
                                }
                                true
                            }

                            // a read-only editor has nothing to insert it into, so tab moves the focus
                            KeyboardCommand.InsertTab -> {
                                if (!readOnly) onValueChange(text.withTabInserted())
                                !readOnly
                            }

                            KeyboardCommand.ZoomIn -> {
                                zoom = (zoom * KEYBOARD_ZOOM_STEP).coerceAtMost(maxZoom)
                                true
                            }

                            KeyboardCommand.ZoomOut -> {
                                zoom = (zoom / KEYBOARD_ZOOM_STEP).coerceAtLeast(minZoom)
                                true
                            }

                            KeyboardCommand.ZoomReset -> {
                                zoom = 1f.coerceIn(minZoom, maxZoom)
                                true
                            }
                        }
                    }
                    .align(Alignment.TopStart)
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .matchParentSize()
                    .background(colors.textFieldBackgroundColor().value)
                    .padding(start = 4.dp, end = 4.dp),
                value = text,
                onValueChange = onValueChange,
                visualTransformation = highlighting,
                // the blinking of its own cursor would record the whole document again each time
                cursorBrush = SolidColor(Color.Unspecified),
                textStyle = fieldTextStyle,
                enabled = enabled,
                readOnly = readOnly,
                onTextLayout = {
                    textLayoutResult = it
                    onTextLayout(it)
                },
                decorationBox = { innerTextField ->
                    Box(propagateMinConstraints = true) {
                        // only here for input, selection and the text layout, see VisibleLines
                        Box(
                            modifier = Modifier.drawWithContent { },
                            propagateMinConstraints = true,
                        ) {
                            innerTextField()
                        }
                        textLayoutResult?.let {
                            VisibleLines(
                                layout = it,
                                selection = text.selection.takeIf { focused },
                                textStyle = fieldTextStyle,
                                window = drawWindow,
                            )
                            if (focused && enabled && !readOnly && text.selection.collapsed) {
                                EditorCursor(
                                    layout = it,
                                    text = text.text,
                                    position = text.selection.start,
                                    color = fieldColors.cursorColor().value,
                                )
                            }
                            Box(
                                modifier = Modifier.placedAt {
                                    IntRect(0, 0, totalSize.width, totalSize.height)
                                },
                            ) {
                                overlay(it)
                            }
                        }
                    }
                },
            )
        }
    }
}


/**
 * The cursor of the editor. It has its own small layer, so blinking only changes the alpha of that layer.
 */
@Composable
private fun EditorCursor(layout: TextLayoutResult, text: String, position: Int, color: Color) {
    var alpha by remember { mutableFloatStateOf(1f) }
    val width = LocalDensity.current.run { CURSOR_WIDTH.roundToPx() }.coerceAtLeast(1)

    if (LocalWindowInfo.current.isWindowFocused) {
        LaunchedEffect(text, position) {
            alpha = 1f
            while (true) {
                delay(CURSOR_BLINK_MS)
                alpha = 1f - alpha
            }
        }
    }

    Box(
        modifier = Modifier
            .placedAt {
                val rect = layout.getCursorRect(position.coerceIn(0, layout.layoutInput.text.length))
                val left = rect.left.roundToInt()
                IntRect(left, rect.top.roundToInt(), left + width, rect.bottom.roundToInt())
            }
            .graphicsLayer { this.alpha = alpha }
            .background(color),
    )
}

private data class DummyData(
    val headingRule: LanguageRule = object : LanguageRule {
        override fun findMatches(text: CharSequence): List<RuleMatch> {
            val PATTERN = "^\\s{0,3}#{1,6} .+".toRegex(RegexOption.MULTILINE)
            return RuleHelper.findRegexMatches(text, PATTERN)
        }
    },

    val dummyRuleBook: LanguageRuleBook = object : LanguageRuleBook {
        override fun getRules() = listOf(headingRule)
    },

    val colorScheme: ColorScheme<SpanStyle> = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule): Set<StyleFactory<SpanStyle>> {
            return setOf { SpanStyle(Color(0xFFFF6D00)) }
        }
    }
)

private val dummyData = DummyData()

@Preview
@Composable
private fun KodeEditorPreview() {
    var text by remember {
        val initialText = """
            # Hello World
            Code: `readResourceFileAsText(R.raw.sample_text)`
            
            ## Secondary headline
            
            This is a listing:
            
            * 1
            * 2
            * 3
            
            # Code Block
            
            ```
            This is a code block.
            ```
        """.trimIndent()
        mutableStateOf(TextFieldValue(text = initialText))
    }

    val languageRuleBook by remember {
        mutableStateOf(dummyData.dummyRuleBook)
    }
    val colorScheme by remember {
        mutableStateOf(dummyData.colorScheme)
    }

    KodeEditor(
        modifier = Modifier.fillMaxSize(),
        text = text,
        languageRuleBook = languageRuleBook,
        colorScheme = colorScheme,
        onValueChange = { text = it }
    )
}
