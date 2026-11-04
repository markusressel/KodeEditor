package de.markusressel.kodeeditor.library.compose

import android.view.inputmethod.BaseInputConnection
import android.view.KeyCharacterMap
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val KEYBOARD_ZOOM_STEP = 1.1f
private const val CURSOR_BLINK_MS = 500L
private val CURSOR_WIDTH = 2.dp
private const val MENU_DELAY_MS = 250L
internal const val GUTTER_ANIMATION_MS = 150
private val HANDLE_EDGE_SIZE = 48.dp
private val HANDLE_EDGE_SPEED = 12.dp
private val TEXT_START_PADDING = 4.dp
private val FOLD_COLUMN_WIDTH = 24.dp
private const val FOLDED_LINE_SELECTION_ALPHA = 0.5f

/** how much of the selection color is in the box behind a folded part of a line */
private const val INLINE_FOLD_SELECTION_ALPHA = 0.7f
private const val FOLD_PLACEHOLDER_ALPHA = 0.6f

/**
 * A code editor for a [TextFieldValue] that the application keeps.
 *
 * The editor keeps the text itself too, see [KodeEditorState]. A [text] that it reported itself to [onValueChange] is
 * not applied again, however late it comes back, so nothing the user typed in the meantime gets lost. Any other value
 * replaces the text of the editor. For that reason a value handed to [onValueChange] can not be rejected by keeping
 * the old one, the application has to set a different one to change it.
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
 * @param onTextLayout callback that is executed when the text changed
 * @param overlay content drawn on top of the editor text, e.g. the cursors and selections of other users.
 * It is placed inside the zoom and scroll transformation and uses the coordinate system of the given
 * [KodeEditorLayout]. The content should not consume pointer input.
 * Its box has the size of the editor, not of the document, which can be too large to measure anything at.
 * Content is not clipped to that box, so draw and place it at text coordinates anywhere in the document.
 * @param keepCursorVisible while the editor has the focus, scroll so the cursor stays visible when it moves or
 * the editor gets smaller (e.g. when the soft keyboard opens and the app shrinks the editor by the IME insets)
 * @param minZoom the smallest zoom factor the user can reach
 * @param maxZoom the largest zoom factor the user can reach
 * @param autoCorrect whether the keyboard may correct words on its own, which is rarely right for code and markup.
 * It can still offer suggestions without this
 * @param lineWrapping whether lines longer than the width of the editor continue in the next row, instead of the view
 * scrolling sideways. They keep one line number. A monospaced font makes this cheap for long documents, all other
 * fonts have to measure each long line
 * @param gutterColumns what the bar next to the text shows, from left to right. No columns remove the bar
 * @param animations what the editor animates
 * @param foldProvider finds what can be folded, which adds a column with arrows to the end of the bar, null folds
 * nothing
 * @param lineDecorations what the editor shows on whole lines, from bottom to top
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
    onTextLayout: (KodeEditorLayout) -> Unit = {},
    overlay: @Composable BoxScope.(layout: KodeEditorLayout) -> Unit = {},
    keepCursorVisible: Boolean = true,
    minZoom: Float = 0.25f,
    maxZoom: Float = 10f,
    autoCorrect: Boolean = false,
    lineWrapping: Boolean = false,
    gutterColumns: List<KodeEditorGutterColumn> = listOf(rememberLineNumberColumn(textStyle, colors, enabled)),
    animations: KodeEditorAnimations = KodeEditorAnimations(),
    foldProvider: KodeEditorFoldProvider? = null,
    lineDecorations: List<KodeEditorLineDecoration> = emptyList(),
) {
    val state = remember { KodeEditorState(text.text, text.selection) }
    val controlled = remember(state) { ControlledValue(state) }
    controlled.onValueChange = onValueChange
    SideEffect { controlled.onValueFromApplication(text) }
    DisposableEffect(state) {
        state.addListener(controlled)
        onDispose { state.removeListener(controlled) }
    }
    KodeEditor(
        state = state,
        languageRuleBook = languageRuleBook,
        colorScheme = colorScheme,
        modifier = modifier,
        colors = colors,
        textStyle = textStyle,
        enabled = enabled,
        readOnly = readOnly,
        onTextLayout = onTextLayout,
        overlay = overlay,
        keepCursorVisible = keepCursorVisible,
        minZoom = minZoom,
        maxZoom = maxZoom,
        autoCorrect = autoCorrect,
        lineWrapping = lineWrapping,
        gutterColumns = gutterColumns,
        animations = animations,
        foldProvider = foldProvider,
        lineDecorations = lineDecorations,
    )
}

/**
 * Tells stale values of the application from real changes.
 *
 * Reported values come back through the state of the application, one recomposition later or more. Newer input may be
 * in the editor by then, and applying the old value would remove it.
 */
internal class ControlledValue(private val state: KodeEditorState) : KodeEditorListener {
    var onValueChange: (TextFieldValue) -> Unit = {}

    private class Reported(val text: String, val selection: TextRange)

    private val reported = ArrayDeque<Reported>()
    private var lastFromApplication: TextFieldValue? = null
    private var applying = false

    override fun onStateChanged() {
        if (applying) return
        reported.addLast(Reported(state.rawText, state.selection))
        if (reported.size > MAX_REPORTED) reported.removeFirst()
        onValueChange(TextFieldValue(state.rawText, state.selection, state.composition))
    }

    fun onValueFromApplication(value: TextFieldValue) {
        if (value === lastFromApplication) return
        lastFromApplication = value
        if (value.text == state.rawText && value.selection == state.selection) {
            reported.clear()
            return
        }
        val echo = reported.indexOfFirst { it.selection == value.selection && it.text == value.text }
        if (echo >= 0) {
            repeat(echo + 1) { reported.removeFirst() }
            return
        }
        reported.clear()
        applying = true
        try {
            state.setText(value.text, value.selection)
        } finally {
            applying = false
        }
    }

    private companion object {
        const val MAX_REPORTED = 256
    }
}

/**
 * A code editor that keeps its text in a [KodeEditorState].
 *
 * @param state the text of the editor, see [rememberKodeEditorState]
 * @param languageRuleBook the language rule book to use for highlighting, remember it so the text is not highlighted
 * again on each recomposition
 * @param colorScheme the color scheme to apply, remember it for the same reason
 * @param modifier compose modifiers
 * @param colors the color scheme to use for highlighting
 * @param textStyle the text style used for the editor text
 * @param enabled whether the editor is enabled
 * @param readOnly whether the contents of the editor can be changed by the user
 * @param onTextLayout callback that is executed when the text changed
 * @param overlay content drawn on top of the editor text, e.g. the cursors and selections of other users.
 * It is placed inside the zoom and scroll transformation and uses the coordinate system of the given
 * [KodeEditorLayout]. The content should not consume pointer input.
 * Its box has the size of the editor, not of the document, which can be too large to measure anything at.
 * Content is not clipped to that box, so draw and place it at text coordinates anywhere in the document.
 * @param keepCursorVisible while the editor has the focus, scroll so the cursor stays visible when it moves or
 * the editor gets smaller (e.g. when the soft keyboard opens and the app shrinks the editor by the IME insets)
 * @param minZoom the smallest zoom factor the user can reach
 * @param maxZoom the largest zoom factor the user can reach
 * @param autoCorrect whether the keyboard may correct words on its own, which is rarely right for code and markup.
 * It can still offer suggestions without this
 * @param lineWrapping whether lines longer than the width of the editor continue in the next row, instead of the view
 * scrolling sideways. They keep one line number. A monospaced font makes this cheap for long documents, all other
 * fonts have to measure each long line
 * @param gutterColumns what the bar next to the text shows, from left to right. No columns remove the bar
 * @param animations what the editor animates
 * @param foldProvider finds what can be folded, which adds a column with arrows to the end of the bar, null folds
 * nothing
 * @param lineDecorations what the editor shows on whole lines, from bottom to top
 */
@Composable
fun KodeEditor(
    state: KodeEditorState,
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
    modifier: Modifier = Modifier,
    colors: KodeEditorColors = KodeEditorDefaults.editorColors(),
    textStyle: TextStyle = LocalTextStyle.current,
    enabled: Boolean = true,
    readOnly: Boolean = enabled.not(),
    onTextLayout: (KodeEditorLayout) -> Unit = {},
    overlay: @Composable BoxScope.(layout: KodeEditorLayout) -> Unit = {},
    keepCursorVisible: Boolean = true,
    minZoom: Float = 0.25f,
    maxZoom: Float = 10f,
    autoCorrect: Boolean = false,
    lineWrapping: Boolean = false,
    gutterColumns: List<KodeEditorGutterColumn> = listOf(rememberLineNumberColumn(textStyle, colors, enabled)),
    animations: KodeEditorAnimations = KodeEditorAnimations(),
    foldProvider: KodeEditorFoldProvider? = null,
    lineDecorations: List<KodeEditorLineDecoration> = emptyList(),
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f.coerceIn(minZoom, maxZoom)) }
    var focused by remember { mutableStateOf(false) }
    val cursorFollower = remember { CursorFollower() }
    val keyboardCursor = remember { KeyboardCursor() }
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val windowInfo = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val textToolbar = LocalTextToolbar.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    // whether the last input was a touch, the handles are only there for touch input
    var touchInput by remember { mutableStateOf(false) }
    var cursorMenuRequested by remember { mutableStateOf(false) }
    var draggedHandle by remember { mutableStateOf<HandleKind?>(null) }
    var dragTarget by remember { mutableStateOf<Offset?>(null) }
    // the word a long press selected, dragging extends the selection from it
    var pressedWord by remember { mutableStateOf<TextRange?>(null) }
    var flinging by remember { mutableStateOf(false) }
    var stoppedFling by remember { mutableStateOf(false) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val highlighting = remember(state, languageRuleBook, colorScheme) {
        EditorHighlighting(state, languageRuleBook, colorScheme)
    }
    DisposableEffect(highlighting) {
        state.spliceObserver = highlighting.spliceObserver
        onDispose { state.spliceObserver = null }
    }
    LaunchedEffect(highlighting) { highlighting.keepUpdated() }

    val folds = remember(state, foldProvider) { foldProvider?.let { EditorFolds(state, it) } }
    DisposableEffect(state, folds) {
        if (folds == null) return@DisposableEffect onDispose {}
        state.foldObserver = folds.spliceObserver
        state.addListener(folds)
        onDispose {
            state.foldObserver = null
            state.removeListener(folds)
        }
    }
    LaunchedEffect(folds) { folds?.inline?.keepUpdated() }

    DisposableEffect(state, view) {
        val link = ViewInputLink(view, state)
        state.inputLink = link
        onDispose { if (state.inputLink === link) state.inputLink = null }
    }
    DisposableEffect(state) {
        val listener = object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) {
                touchInput = false
            }
        }
        state.addListener(listener)
        onDispose { state.removeListener(listener) }
    }

    LaunchedEffect(minZoom, maxZoom) {
        zoom = zoom.coerceIn(minZoom, maxZoom)
    }

    val menuActions = remember(state, readOnly, clipboard) {
        HandleMenu(
            state = state,
            readOnly = readOnly,
            clipboardText = { clipboard.getText() },
            setClipboardText = { clipboard.setText(it) },
        )
    }

    val keyboardActions = remember(view, menuActions, state) {
        object : KeyboardActions {
            private val keySender = BaseInputConnection(view, false)

            // on the queue of the window, like a key of a hardware keyboard, which ends up in the key handler below
            override fun onKeyEvent(event: AndroidKeyEvent) = keySender.sendKeyEvent(event)

            override fun onContextMenuAction(id: Int): Boolean {
                when (id) {
                    android.R.id.selectAll -> menuActions.selectAll()
                    android.R.id.copy -> if (!state.selection.collapsed) menuActions.copy()
                    android.R.id.cut -> if (!state.selection.collapsed) menuActions.cut()
                    android.R.id.paste, android.R.id.pasteAsPlainText -> menuActions.paste()
                    android.R.id.undo -> state.undo()
                    android.R.id.redo -> state.redo()
                    else -> return false
                }
                return true
            }
        }
    }

    val fieldColors = colors.textFieldColors(enabled = enabled).value
    val fieldTextColor = fieldColors.textColor(enabled).value
    val fieldTextStyle = textStyle.copy(color = textStyle.color.takeOrElse { fieldTextColor })
    val foldColumnWidth = LocalDensity.current.run { FOLD_COLUMN_WIDTH.roundToPx() }
    val foldColor = colors.lineNumberTextColor().value.takeOrElse { fieldTextStyle.color }
    val foldColumn = remember(folds, foldColumnWidth, foldColor) {
        folds?.let { FoldColumn(it, foldColumnWidth, foldColor) }
    }
    val columns = if (foldColumn == null) gutterColumns else gutterColumns + foldColumn
    val selectionColor = LocalTextSelectionColors.current.backgroundColor
    val foldedBackground = colors.foldedLineBackgroundColor().value
        .takeOrElse { selectionColor.copy(alpha = selectionColor.alpha * FOLDED_LINE_SELECTION_ALPHA) }
    val foldPlaceholderColor = colors.foldPlaceholderColor().value
        .takeOrElse { foldColor.copy(alpha = foldColor.alpha * FOLD_PLACEHOLDER_ALPHA) }
    val foldDecoration = remember(folds, foldedBackground, foldPlaceholderColor) {
        folds?.let { FoldedLineDecoration(it, foldedBackground, foldPlaceholderColor) }
    }
    val decorations = if (foldDecoration == null) lineDecorations else listOf(foldDecoration) + lineDecorations
    val inlineFoldBackground = colors.inlineFoldBackgroundColor().value
        .takeOrElse { selectionColor.copy(alpha = selectionColor.alpha * INLINE_FOLD_SELECTION_ALPHA) }
    val inlineFoldText = colors.inlineFoldTextColor().value.takeOrElse { foldColor }
    val currentDecorations by rememberUpdatedState(decorations)
    val targetGutterWidth by remember(state, gutterColumns, foldColumn) {
        derivedStateOf { columns.sumOf { it.width(state.lineCount) } }
    }
    // a whole new text is not a change of the text that is there
    val textReplacements = state.textReplacements
    val seenTextReplacements = remember(state) { intArrayOf(textReplacements) }
    val gutterSnaps = !animations.gutterWidth || seenTextReplacements[0] != textReplacements
    SideEffect { seenTextReplacements[0] = textReplacements }
    val gutterWidth by animateIntAsState(
        targetValue = targetGutterWidth,
        animationSpec = if (gutterSnaps) snap() else tween(GUTTER_ANIMATION_MS),
        label = "gutterWidth",
    )

    // Text Editor
    var totalSize by remember { mutableStateOf(IntSize.Zero) }
    val textStartPadding = LocalDensity.current.run { TEXT_START_PADDING.toPx() }
    val geometry by remember(textStartPadding) {
        derivedStateOf { EditorGeometry(totalSize, gutterWidth, textStartPadding, zoom) }
    }
    // the bar animates on its own, what depends on the room of the text only has to change once for that
    val settledGeometry by remember(textStartPadding) {
        derivedStateOf { EditorGeometry(totalSize, targetGutterWidth, textStartPadding, zoom) }
    }
    val drawWindow = remember(textStartPadding) {
        derivedStateOf {
            DrawWindow.around(Offset(offset.x - textStartPadding, offset.y), geometry.visibleSize)
        }
    }

    val wrapWidth = remember(textStartPadding) {
        derivedStateOf {
            val visible = settledGeometry.visibleSize.width
            floor(visible - 2 * textStartPadding.roundToInt()).coerceAtLeast(0f)
        }
    }

    val measurer = rememberTextMeasurer(cacheSize = LINE_LAYOUT_CACHE_SIZE)
    val breakMeasurer = rememberTextMeasurer(cacheSize = 0)
    val layout = remember(
        state, measurer, breakMeasurer, fieldTextStyle, highlighting, lineWrapping, animations.reflow, animations.folding, folds,
        inlineFoldText,
    ) {
        EditorLineLayout(
            state = state,
            measurer = measurer,
            textStyle = fieldTextStyle,
            highlighting = highlighting,
            window = drawWindow,
            breaker = if (lineWrapping) RowBreaker(breakMeasurer, fieldTextStyle) else null,
            wrapWidth = { wrapWidth.value },
            animateReflow = animations.reflow,
            folds = folds,
            animateFolds = animations.folding,
            foldPlaceholderStyle = SpanStyle(color = inlineFoldText),
        )
    }
    DisposableEffect(state, layout) {
        state.rowObserver = layout.rowObserver
        onDispose { state.rowObserver = null }
    }

    LaunchedEffect(layout) {
        snapshotFlow { state.version }.collect { currentOnTextLayout(layout) }
    }

    val maxOffset by remember(layout, textStartPadding) {
        derivedStateOf {
            val size = layout.contentSize
            settledGeometry.maxOffset(IntSize(size.width + 2 * textStartPadding.roundToInt(), size.height))
        }
    }

    // what the user works on, the cursor and the moving end of a selection first
    fun anchors() = if (focused) listOf(state.selection.end, state.selection.start) else emptyList()

    // the cursor or the selection stays where it is when the lines wrap differently, otherwise the top of the view does
    LaunchedEffect(layout, wrapWidth.value) {
        layout.takePositionAfterResize(offset.y, anchors(), settledGeometry.visibleSize.height)?.let { y ->
            val before = offset.y
            offset = EditorGeometry.coerce(offset.copy(y = y), maxOffset)
            layout.onScrolledByReflow(offset.y - before)
        }
    }

    // the text can get shorter and the zoom can change, the view must not stay beyond the end of the text
    LaunchedEffect(maxOffset) {
        val before = offset.y
        offset = EditorGeometry.coerce(offset, maxOffset)
        layout.onScrolledByReflow(offset.y - before)
    }

    val selection = state.selection

    // Follow the cursor while the user edits: when the selection moves or the editor changes its size.
    // Panning or remote changes of the text alone don't scroll.
    LaunchedEffect(keepCursorVisible, focused, selection, totalSize, zoom, wrapWidth.value) {
        val follow = cursorFollower.shouldFollow(selection, focused)
        // a dragged handle scrolls the view itself
        if (!keepCursorVisible || !follow || totalSize == IntSize.Zero || draggedHandle != null) {
            return@LaunchedEffect
        }
        offset = CursorVisibility.offsetFor(
            offset = offset,
            cursor = layout.getCursorRect(selection.end).translate(textStartPadding, 0f),
            viewport = settledGeometry.visibleSize,
            maxOffset = maxOffset,
        )
    }

    /** Zooms without fingers to zoom around, so the cursor or the selection stays in its place if it is visible */
    fun zoomAroundCursor(newZoom: Float) {
        val visible = geometry.visibleSize
        val anchor = anchors().map { layout.getCursorRect(it).topLeft }
            .firstOrNull { it.y >= offset.y && it.y < offset.y + visible.height }
        if (anchor != null) {
            val moved = zoomedOffset(
                offset = offset,
                oldZoom = zoom,
                newZoom = newZoom,
                centroid = geometry.toEditor(anchor, offset),
                pan = Offset.Zero,
                oldStart = geometry.gutterOnScreen(zoom),
                newStart = geometry.gutterOnScreen(newZoom),
            )
            offset = Offset(moved.x.coerceAtLeast(0f), moved.y.coerceAtLeast(0f))
        }
        zoom = newZoom
    }

    fun offsetAt(position: Offset) = layout.getOffsetForPosition(geometry.toText(position, offset))

    /** @return true if a column of the bar took the tap */
    fun tapGutter(position: Offset): Boolean {
        if (position.x >= geometry.gutterOnScreen) return false
        val lines = layout.lineCount
        val x = position.x / geometry.gutterScale + targetGutterWidth - geometry.gutterWidth
        val column = gutterColumnAt(columns, lines, x) ?: return false
        return column.onTap(layout.getLineForVerticalPosition(geometry.toText(position, offset).y))
    }

    val gestures = remember(state, layout, enabled, readOnly) {
        object : EditorGestureHandler {
            private fun focusAndShowKeyboard() {
                focusRequester.requestFocus()
                if (!readOnly) keyboard?.show()
            }

            override fun onDown(type: PointerType) {
                touchInput = type == PointerType.Touch
                cursorMenuRequested = false
                stoppedFling = flinging
            }

            override fun onTap(position: Offset) {
                // this touch only stops the fling
                if (!enabled || stoppedFling || tapGutter(position)) return
                val point = geometry.toText(position, offset)
                if (layout.tapTrailingText(point, currentDecorations)) return
                focusAndShowKeyboard()
                val fold = layout.foldedRangeAt(point)
                state.setSelection(TextRange(offsetAt(position).let { if (fold == null) it else it.coerceIn(fold.min, fold.max) }))
                fold?.let(layout::openFold)
            }

            override fun onDoubleTap(position: Offset) {
                // two quick taps on a button of the bar are two taps
                if (!enabled || stoppedFling || tapGutter(position)) return
                focusAndShowKeyboard()
                state.setSelection(layout.getWordBoundary(offsetAt(position)))
            }

            override fun onSelectionStart(position: Offset, type: PointerType) {
                if (!enabled) return
                focusAndShowKeyboard()
                val target = offsetAt(position)
                val word = layout.foldedRangeAt(geometry.toText(position, offset))
                    ?: if (type == PointerType.Mouse) TextRange(target) else layout.getWordBoundary(target)
                if (type != PointerType.Mouse) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.setSelection(word)
                // nothing to select here, so the menu offers what can be done at the cursor
                cursorMenuRequested = word.collapsed && type != PointerType.Mouse
                pressedWord = word
                dragTarget = position
                draggedHandle = HandleKind.SelectionEnd
            }

            override fun onSelectionDrag(position: Offset) {
                val word = pressedWord ?: return
                dragTarget = position
                val target = offsetAt(position)
                state.setSelection(TextRange(minOf(word.min, target), maxOf(word.max, target)))
            }

            override fun onSelectionEnd() {
                pressedWord = null
                dragTarget = null
                draggedHandle = null
            }
        }
    }

    /** the point the handle at this text position points at, null outside the visible text */
    fun handleAnchor(position: Int): Offset? {
        val cursor = layout.shownCursorRect(position)
        val point = geometry.toEditor(cursor.bottomCenter, offset)
        val visibleX = point.x >= geometry.gutterOnScreen && point.x <= totalSize.width
        return point.takeIf { visibleX && it.y >= 0f && it.y <= totalSize.height }
    }

    fun moveHandle(kind: HandleKind, target: Offset) {
        val word = pressedWord
        if (word != null) {
            gestures.onSelectionDrag(target)
            return
        }
        val point = geometry.toText(target, offset)
        val position = layout.getOffsetForPosition(Offset(point.x, point.y - layout.lineHeight / 2))
        val current = state.selection
        state.setSelection(
            when (kind) {
                HandleKind.Cursor -> TextRange(position)
                HandleKind.SelectionStart -> TextRange(
                    position.coerceAtMost(current.max - 1).coerceAtLeast(0),
                    current.max,
                )

                HandleKind.SelectionEnd -> TextRange(
                    current.min,
                    position.coerceAtLeast(current.min + 1),
                )
            },
        )
    }

    // Near an edge of the editor a dragged handle scrolls the view, so it can reach the whole text.
    val edgeSize = LocalDensity.current.run { HANDLE_EDGE_SIZE.toPx() }
    val edgeSpeed = LocalDensity.current.run { HANDLE_EDGE_SPEED.toPx() }
    LaunchedEffect(draggedHandle) {
        val kind = draggedHandle ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val target = dragTarget ?: continue
            val area = Size(totalSize.width.toFloat(), totalSize.height.toFloat())
            val scrolled = EditorGeometry.coerce(
                offset + HandleDrag.edgeScroll(target, area, geometry.gutterOnScreen, edgeSize, edgeSpeed) / zoom,
                maxOffset,
            )
            if (scrolled != offset) {
                offset = scrolled
                moveHandle(kind, target)
            }
        }
    }

    fun showMenu() {
        val current = state.selection
        val coordinates = editorCoordinates?.takeIf { it.isAttached } ?: return
        val startRect = layout.getCursorRect(current.min)
        val endRect = layout.getCursorRect(current.max)
        val top = coordinates.localToRoot(geometry.toEditor(startRect.topLeft, offset))
        val bottom = coordinates.localToRoot(geometry.toEditor(endRect.bottomRight, offset))
        val hasSelection = !current.collapsed
        textToolbar.showMenu(
            rect = Rect(
                left = minOf(top.x, bottom.x),
                top = top.y,
                right = maxOf(top.x, bottom.x),
                bottom = bottom.y,
            ),
            onCopyRequested = menuActions::copy.takeIf { hasSelection },
            onPasteRequested = menuActions::paste.takeIf { !readOnly && clipboard.hasText() },
            onCutRequested = menuActions::cut.takeIf { !readOnly && hasSelection },
            onSelectAllRequested = menuActions::selectAll.takeIf { current.length < state.rawText.length },
        )
    }

    LaunchedEffect(selection, focused, touchInput, cursorMenuRequested, draggedHandle, offset, zoom) {
        val wanted = focused && touchInput && draggedHandle == null &&
            (!selection.collapsed || cursorMenuRequested)
        if (textToolbar.status == TextToolbarStatus.Shown) textToolbar.hide()
        if (wanted) {
            delay(MENU_DELAY_MS)
            showMenu()
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled || event.type != KeyEventType.KeyDown || !event.isFromTextKeyboard()) return false
        val current = state.selection
        val editable = !readOnly
        return when (val command = event.toKeyboardCommand()) {
            null -> editable && event.insertTypedCharacter(state)

            is KeyboardCommand.Move -> {
                val target = keyboardCursor.move(
                    text = state.rawText,
                    selection = current,
                    move = command.move,
                    extend = event.isShiftPressed,
                    lines = EditorTextLines(layout),
                    pageHeight = totalSize.height / zoom,
                )
                touchInput = false
                state.setSelection(target)
                true
            }

            // a read-only editor has nothing to insert it into, so tab moves the focus
            KeyboardCommand.InsertTab -> {
                if (editable) state.replaceSelection("\t")
                editable
            }

            KeyboardCommand.InsertLine -> {
                if (editable) state.replaceSelection("\n")
                editable
            }

            // like in IntelliJ: the current line and a selection in it stay as they are
            KeyboardCommand.StartNewLine -> {
                if (editable) {
                    val line = state.lineOf(current.end)
                    val end = state.lineEnd(line)
                    state.localEdit(end, end, "\n" + indentOf(state.rawText, state.lineStart(line)))
                }
                editable
            }

            KeyboardCommand.Backspace -> {
                if (editable) deleteTo(state, previousCharacter(state.rawText, current.min))
                editable
            }

            KeyboardCommand.Delete -> {
                if (editable) deleteTo(state, nextCharacter(state.rawText, current.max))
                editable
            }

            KeyboardCommand.BackspaceWord -> {
                if (editable) deleteTo(state, previousWord(state.rawText, current.min))
                editable
            }

            KeyboardCommand.DeleteWord -> {
                if (editable) deleteTo(state, nextWord(state.rawText, current.max))
                editable
            }

            KeyboardCommand.Copy -> {
                if (!current.collapsed) menuActions.copy()
                true
            }

            KeyboardCommand.Cut -> {
                if (editable && !current.collapsed) menuActions.cut()
                editable
            }

            KeyboardCommand.Paste -> {
                if (editable) menuActions.paste()
                editable
            }

            KeyboardCommand.SelectAll -> {
                menuActions.selectAll()
                true
            }

            KeyboardCommand.Undo -> {
                if (editable) state.undo()
                editable
            }

            KeyboardCommand.Redo -> {
                if (editable) state.redo()
                editable
            }

            KeyboardCommand.ZoomIn -> {
                zoomAroundCursor((zoom * KEYBOARD_ZOOM_STEP).coerceAtMost(maxZoom))
                true
            }

            KeyboardCommand.ZoomOut -> {
                zoomAroundCursor((zoom / KEYBOARD_ZOOM_STEP).coerceAtLeast(minZoom))
                true
            }

            KeyboardCommand.ZoomReset -> {
                zoomAroundCursor(1f.coerceIn(minZoom, maxZoom))
                true
            }
        }
    }

    val handleColor = LocalTextSelectionColors.current.handleColor
    val hasSelection = !selection.collapsed
    val handlesWanted = focused && touchInput
    val handles = when {
        !handlesWanted -> emptyList()
        hasSelection -> listOf(HandleKind.SelectionStart, HandleKind.SelectionEnd)
        !readOnly && state.text.isNotEmpty() -> listOf(HandleKind.Cursor)
        else -> emptyList()
    }
    Box(
        modifier = Modifier
            .clipToBounds()
            .then(modifier)
            .onPlaced { editorCoordinates = it }
            // the text only covers the document, which is smaller than the editor for a short text or a low zoom
            .background(colors.textFieldBackgroundColor().value)
            // on the parent, so these see the events of the zoom layout and the handles, without taking them away
            .pointerInput(gestures) { detectEditorGestures(gestures) }
            // the keyboard app gets the keys first, and it would turn this one into a line break or drop it
            .onInterceptKeyBeforeSoftKeyboard { event ->
                val intercept = event.toKeyboardCommand() == KeyboardCommand.StartNewLine &&
                    enabled && !readOnly && event.isFromTextKeyboard()
                if (intercept) onKeyEvent(event)
                intercept
            }
            .onPreviewKeyEvent { onKeyEvent(it) }
            .onFocusChanged {
                if (focused && !it.isFocused) keyboard?.hide()
                focused = it.isFocused
            }
            .focusRequester(focusRequester)
            .editorInput(state, enabled && !readOnly, autoCorrect, keyboardActions)
            .semantics {
                if (!enabled) disabled()
                editableText = AnnotatedString(state.text)
                textSelectionRange = state.selection
                setSelection { start, end, _ ->
                    if (!enabled || start !in 0..state.rawText.length || end !in 0..state.rawText.length) {
                        return@setSelection false
                    }
                    state.setSelection(TextRange(start, end))
                    true
                }
                onClick {
                    if (!enabled) return@onClick false
                    focusRequester.requestFocus()
                    if (!readOnly) keyboard?.show()
                    true
                }
                if (enabled && !readOnly) {
                    setText {
                        state.setText(it.text)
                        true
                    }
                    insertTextAtCursor {
                        state.replaceSelection(it.text)
                        true
                    }
                }
            }
            .focusable(enabled = enabled),
    ) {
        EditorGutter(
            modifier = Modifier
                .zIndex(1f)
                .matchParentSize(),
            layout = layout,
            columns = columns,
            backgroundColor = colors.lineNumberBackgroundColor().value,
            window = drawWindow,
            geometry = { geometry },
            targetWidth = { targetGutterWidth },
            offsetY = { offset.y },
            fadeLineNumbers = animations.lineNumbers,
        )

        // all of the editor reacts to gestures, but the text does not show up behind the bar
        Box(
            modifier = Modifier
                .zIndex(0f)
                .matchParentSize()
                .onSizeChanged { size -> totalSize = size }
                .drawWithContent {
                    clipRect(left = geometry.gutterOnScreen) { this@drawWithContent.drawContent() }
                },
        ) {
        ZoomLayout(
            modifier = Modifier.matchParentSize(),
            offset = offset,
            zoom = zoom,
            minZoom = minZoom,
            maxZoom = maxZoom,
            onOffsetChanged = {
                cursorFollower.onUserMovedView()
                offset = EditorGeometry.coerce(it, maxOffset)
            },
            onZoomChanged = {
                cursorFollower.onUserMovedView()
                zoom = it
            },
            coerceOffset = { EditorGeometry.coerce(it, maxOffset) },
            onFlingChanged = { flinging = it },
            contentStart = { geometry.gutterOnScreen(it) },
        ) {
            Box(modifier = Modifier.matchParentSize().padding(start = TEXT_START_PADDING)) {
                if (decorations.isNotEmpty()) {
                    LineDecorationLayer(
                        layout = layout,
                        decorations = decorations,
                        window = drawWindow,
                        textPadding = textStartPadding,
                    )
                }
                SelectionLayer(
                    layout = layout,
                    selection = { state.selection.takeIf { focused } },
                    window = drawWindow,
                )
                VisibleLines(
                    layout = layout,
                    window = drawWindow,
                    decorations = decorations,
                    foldBoxColor = inlineFoldBackground,
                )
                if (focused && enabled && !readOnly && selection.collapsed) {
                    EditorCursor(
                        state = state,
                        layout = layout,
                        color = fieldColors.cursorColor().value,
                        blinking = windowInfo.isWindowFocused,
                    )
                }
                Box(
                    modifier = Modifier.placedAt {
                        IntRect(0, 0, totalSize.width, totalSize.height)
                    },
                ) {
                    overlay(remember(layout) { ShownEditorLayout(layout) })
                }
            }
        }
        }

        Box(modifier = Modifier.zIndex(2f).matchParentSize()) {
            handles.forEach { kind ->
                key(kind) {
                    SelectionHandle(
                        kind = kind,
                        color = handleColor,
                        anchor = {
                            handleAnchor(
                                if (kind == HandleKind.SelectionEnd) state.selection.max
                                else state.selection.min
                            )
                        },
                        editorCoordinates = { editorCoordinates },
                        onDrag = {
                            dragTarget = it
                            draggedHandle = kind
                            moveHandle(kind, it)
                        },
                        onDragEnd = {
                            draggedHandle = null
                            dragTarget = null
                        },
                        onTap = {
                            if (kind == HandleKind.Cursor) cursorMenuRequested = !cursorMenuRequested
                        },
                    )
                }
            }
        }
    }
}

/** removes the selection, or without one the text up to [target] from the cursor */
private fun deleteTo(state: KodeEditorState, target: Int) {
    val selection = state.selection
    when {
        !selection.collapsed -> state.replaceSelection("")
        target < selection.min -> state.localEdit(target, selection.min, "")
        target > selection.max -> state.localEdit(selection.max, target, "")
    }
}

/** types the character of a key of a hardware keyboard */
private fun KeyEvent.insertTypedCharacter(state: KodeEditorState): Boolean {
    val code = utf16CodePoint
    val isAccentOnly = code and KeyCharacterMap.COMBINING_ACCENT != 0
    if (code == 0 || isAccentOnly || Character.isISOControl(code) || isCtrlPressed || isMetaPressed) return false
    state.replaceSelection(String(Character.toChars(code)))
    return true
}

/**
 * The cursor of the editor. It has its own small layer, so blinking only changes the alpha of that layer.
 */
@Composable
private fun EditorCursor(state: KodeEditorState, layout: EditorLineLayout, color: Color, blinking: Boolean) {
    var alpha by remember { mutableFloatStateOf(1f) }
    val width = LocalDensity.current.run { CURSOR_WIDTH.roundToPx() }.coerceAtLeast(1)

    if (blinking) {
        LaunchedEffect(state.version, state.selection) {
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
                val rect = layout.shownCursorRect(state.selection.end)
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
