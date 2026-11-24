package de.markusressel.kodeeditor.library.compose

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAnimations
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAppearance
import de.markusressel.kodeeditor.library.compose.cursor.EditorCursor
import de.markusressel.kodeeditor.library.compose.cursor.EditorTextMenuEffect
import de.markusressel.kodeeditor.library.compose.cursor.HandleDrag
import de.markusressel.kodeeditor.library.compose.cursor.HandleEdgeScrollEffect
import de.markusressel.kodeeditor.library.compose.cursor.KodeEditorRemoteCursor
import de.markusressel.kodeeditor.library.compose.cursor.RemoteCaretLayer
import de.markusressel.kodeeditor.library.compose.cursor.RemoteCursorLabels
import de.markusressel.kodeeditor.library.compose.cursor.RemoteSelectionLayer
import de.markusressel.kodeeditor.library.compose.cursor.SelectionHandle
import de.markusressel.kodeeditor.library.compose.cursor.rememberRemoteCursorGlides
import de.markusressel.kodeeditor.library.compose.fold.EditorFolds
import de.markusressel.kodeeditor.library.compose.fold.FoldColumn
import de.markusressel.kodeeditor.library.compose.fold.FoldedLineDecoration
import de.markusressel.kodeeditor.library.compose.fold.KodeEditorFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.KodeEditorFoldProviders
import de.markusressel.kodeeditor.library.compose.gutter.EditorGutter
import de.markusressel.kodeeditor.library.compose.gutter.KodeEditorGutterColumn
import de.markusressel.kodeeditor.library.compose.gutter.KodeEditorLineDecoration
import de.markusressel.kodeeditor.library.compose.gutter.LineDecorationLayer
import de.markusressel.kodeeditor.library.compose.gutter.gutterColumnAt
import de.markusressel.kodeeditor.library.compose.gutter.rememberLineNumberColumn
import de.markusressel.kodeeditor.library.compose.input.EditorClipboardActions
import de.markusressel.kodeeditor.library.compose.input.EditorKeyboardActions
import de.markusressel.kodeeditor.library.compose.input.EditorKeys
import de.markusressel.kodeeditor.library.compose.input.EditorTouch
import de.markusressel.kodeeditor.library.compose.input.EditorTouchGestures
import de.markusressel.kodeeditor.library.compose.input.HandleKind
import de.markusressel.kodeeditor.library.compose.input.KeyZoom
import de.markusressel.kodeeditor.library.compose.input.KeyboardCommand
import de.markusressel.kodeeditor.library.compose.input.KeyboardCursor
import de.markusressel.kodeeditor.library.compose.input.KodeEditorKeyBinding
import de.markusressel.kodeeditor.library.compose.input.ViewInputLink
import de.markusressel.kodeeditor.library.compose.input.detectEditorGestures
import de.markusressel.kodeeditor.library.compose.input.editorInput
import de.markusressel.kodeeditor.library.compose.input.editorSemantics
import de.markusressel.kodeeditor.library.compose.input.isFromTextKeyboard
import de.markusressel.kodeeditor.library.compose.input.toKeyboardCommand
import de.markusressel.kodeeditor.library.compose.layout.EditorHighlighting
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.layout.EditorTextLines
import de.markusressel.kodeeditor.library.compose.layout.KodeEditorLayout
import de.markusressel.kodeeditor.library.compose.layout.LINE_LAYOUT_CACHE_SIZE
import de.markusressel.kodeeditor.library.compose.layout.RemoteScrollAnchorEffect
import de.markusressel.kodeeditor.library.compose.layout.RowBreaker
import de.markusressel.kodeeditor.library.compose.layout.SelectionLayer
import de.markusressel.kodeeditor.library.compose.layout.ShownEditorLayout
import de.markusressel.kodeeditor.library.compose.layout.VisibleLines
import de.markusressel.kodeeditor.library.compose.layout.placedAt
import de.markusressel.kodeeditor.library.compose.layout.remoteScrollAnchorIn
import de.markusressel.kodeeditor.library.compose.layout.withEmbeddedFallback
import de.markusressel.kodeeditor.library.compose.search.KodeEditorSearch
import de.markusressel.kodeeditor.library.compose.search.SearchFoldsEffect
import de.markusressel.kodeeditor.library.compose.search.SearchLayer
import de.markusressel.kodeeditor.library.compose.search.toRegex
import de.markusressel.kodeeditor.library.compose.state.AppearingTextEffect
import de.markusressel.kodeeditor.library.compose.state.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.SplicePhase
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodeeditor.library.compose.state.rememberKodeEditorState
import de.markusressel.kodeeditor.library.compose.view.CursorFollower
import de.markusressel.kodeeditor.library.compose.view.CursorVisibility
import de.markusressel.kodeeditor.library.compose.view.DrawWindow
import de.markusressel.kodeeditor.library.compose.view.EditorGeometry
import de.markusressel.kodeeditor.library.compose.view.EditorScrollbars
import de.markusressel.kodeeditor.library.compose.view.EditorViewportAdapter
import de.markusressel.kodeeditor.library.compose.view.KodeEditorScrollbarStyle
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState
import de.markusressel.kodeeditor.library.compose.view.ZoomLayout
import de.markusressel.kodeeditor.library.compose.view.rememberKodeEditorViewState
import de.markusressel.kodeeditor.library.compose.view.zoomedOffset
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import de.markusressel.kodehighlighter.core.rule.RuleHelper
import de.markusressel.kodehighlighter.core.rule.RuleMatch
import kotlin.math.floor
import kotlin.math.roundToInt

private const val KEYBOARD_ZOOM_STEP = 1.1f
internal const val GUTTER_ANIMATION_MS = 150
internal val TEXT_START_PADDING = 4.dp
private val FOLD_COLUMN_WIDTH = 24.dp

/**
 * A code editor that keeps its text in a [KodeEditorState].
 *
 * @param state the text of the editor, see [rememberKodeEditorState]
 * @param languageRuleBook the language rule book to use for highlighting, remember it so the text is not highlighted
 * again on each recomposition
 * @param colorScheme the color scheme to apply, remember it for the same reason. Rules it has no style for, like those
 * of a language in a code block of Markdown, get the colors of `GitHubDarkTheme` or `GitHubLightTheme`, whichever fits
 * the brightness of `textFieldBackgroundColor`
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
 * @param keepViewOnRemoteChanges whether text that others insert or remove in front of the cursor, or in front of the
 * first visible line while the cursor is not in view, scrolls the view with it, so the cursor or that line stays where
 * it is on the screen
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
 * nothing. The built-in provider of the language of the [languageRuleBook] by default, see [KodeEditorFoldProviders]
 * @param lineDecorations what the editor shows on whole lines, from bottom to top
 * @param keyBindings what the keys of a hardware keyboard do, a key without a binding is left to the rest of the app
 * @param viewState scrolls to a place in the text and tells what is visible
 * @param onUserMovedView called once per gesture when the user starts to pan or zoom, also for a zoom with the keyboard
 * for a dragged selection handle that scrolls the view and for a dragged scrollbar. Not for what moves the view on
 * its own, like [viewState], following the cursor or lines that wrap differently
 * @param scrollbars whether scrollbars show up while the view moves, whose thumbs can be dragged to move it
 * @param scrollbarStyle the size of the scrollbars and when they show and hide
 * @param search highlights the matches of a search, and unfolds what hides its current match
 * @param remoteCursors the cursors and selections of other users, drawn on top of the text. They glide to their new
 * place with `cursorGlide` of [KodeEditorAppearance], and jump without an appearance. A user who leaves fades out
 * @param remoteCursorLabel shows the name of another user at its first cursor, above it, or below it on the first row
 * and at the start of a wrapped row, and inside the right edge of the text.
 * It is placed by the editor, and only composed again when its [KodeEditorRemoteCursor] changes
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
    keepViewOnRemoteChanges: Boolean = true,
    minZoom: Float = 0.25f,
    maxZoom: Float = 10f,
    autoCorrect: Boolean = false,
    lineWrapping: Boolean = false,
    gutterColumns: List<KodeEditorGutterColumn> = listOf(rememberLineNumberColumn(textStyle, colors, enabled)),
    animations: KodeEditorAnimations = KodeEditorAnimations(),
    foldProvider: KodeEditorFoldProvider? = KodeEditorFoldProviders.forLanguage(languageRuleBook.language),
    lineDecorations: List<KodeEditorLineDecoration> = emptyList(),
    keyBindings: List<KodeEditorKeyBinding> = KodeEditorDefaults.keyBindings,
    viewState: KodeEditorViewState = rememberKodeEditorViewState(),
    onUserMovedView: () -> Unit = {},
    scrollbars: Boolean = true,
    scrollbarStyle: KodeEditorScrollbarStyle = KodeEditorScrollbarStyle(),
    search: KodeEditorSearch? = null,
    remoteCursors: List<KodeEditorRemoteCursor> = emptyList(),
    remoteCursorLabel: (@Composable (KodeEditorRemoteCursor) -> Unit)? = null,
) {
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    var offset by offsetState
    val zoomState = remember { mutableFloatStateOf(1f.coerceIn(minZoom, maxZoom)) }
    var zoom by zoomState
    val currentZoomRange by rememberUpdatedState(minZoom..maxZoom)
    var focused by remember { mutableStateOf(false) }
    val cursorFollower = remember { CursorFollower() }
    val keyboardCursor = remember { KeyboardCursor() }
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    val windowInfo = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val touch = remember { EditorTouch() }
    // a dragged scrollbar takes the view from a fling
    var flingStops by remember { mutableIntStateOf(0) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentOnTextLayout by rememberUpdatedState(onTextLayout)
    val currentOnUserMovedView by rememberUpdatedState(onUserMovedView)
    val currentAnimations by rememberUpdatedState(animations)
    val viewScope = rememberCoroutineScope()

    val darkBackground = colors.textFieldBackgroundColor().value.let { background ->
        if (background.isSpecified && background.alpha > 0f) background.luminance() < 0.5f else isSystemInDarkTheme()
    }
    val highlighting = remember(state, languageRuleBook, colorScheme, darkBackground) {
        EditorHighlighting(state, languageRuleBook, colorScheme.withEmbeddedFallback(darkBackground))
    }
    DisposableEffect(highlighting) {
        state.addSpliceObserver(SplicePhase.Highlighting, highlighting.spliceObserver)
        onDispose { state.removeSpliceObserver(SplicePhase.Highlighting, highlighting.spliceObserver) }
    }
    LaunchedEffect(highlighting) { highlighting.keepUpdated() }

    val folds = remember(state, foldProvider) { foldProvider?.let { EditorFolds(state, it) } }
    val appearance = animations.appearance
    val appearSlide = with(LocalDensity.current) { (appearance?.slide ?: 0.dp).toPx() }
    AppearingTextEffect(state, appearance)

    DisposableEffect(state, folds) {
        if (folds == null) return@DisposableEffect onDispose {}
        state.addSpliceObserver(SplicePhase.Folds, folds.spliceObserver)
        state.addListener(folds)
        onDispose {
            state.removeSpliceObserver(SplicePhase.Folds, folds.spliceObserver)
            state.removeListener(folds)
        }
    }
    LaunchedEffect(folds) { folds?.inline?.keepUpdated() }
    if (search != null) SearchFoldsEffect(search, folds)

    DisposableEffect(state, view) {
        val link = ViewInputLink(view, state)
        state.inputLink = link
        onDispose { if (state.inputLink === link) state.inputLink = null }
    }
    DisposableEffect(state) {
        val listener = object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) {
                touch.touchInput = false
            }
        }
        state.addListener(listener)
        onDispose { state.removeListener(listener) }
    }

    LaunchedEffect(minZoom, maxZoom) {
        zoom = zoom.coerceIn(minZoom, maxZoom)
    }

    val menuActions = remember(state, readOnly, clipboard) {
        EditorClipboardActions(
            state = state,
            readOnly = readOnly,
            clipboardText = { clipboard.getText() },
            setClipboardText = { clipboard.setText(it) },
        )
    }

    val currentFolds by rememberUpdatedState(folds)
    val keyboardActions = remember(view, menuActions, state) {
        EditorKeyboardActions(view, state, menuActions) { currentFolds }
    }

    val shownColors = colors.resolve(enabled, textStyle)
    val fieldTextStyle = shownColors.textStyle
    val foldColumnWidth = LocalDensity.current.run { FOLD_COLUMN_WIDTH.roundToPx() }
    val foldColor = shownColors.gutterText
    val foldColumn = remember(folds, foldColumnWidth, foldColor) {
        folds?.let { FoldColumn(it, foldColumnWidth, foldColor) }
    }
    val columns = if (foldColumn == null) gutterColumns else gutterColumns + foldColumn
    val foldedBackground = shownColors.foldedLineBackground
    val foldPlaceholderColor = shownColors.foldPlaceholder
    val foldDecoration = remember(folds, foldedBackground, foldPlaceholderColor) {
        folds?.let { FoldedLineDecoration(it, foldedBackground, foldPlaceholderColor) }
    }
    val decorations = if (foldDecoration == null) lineDecorations else listOf(foldDecoration) + lineDecorations
    val inlineFoldText = shownColors.inlineFoldText
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
        state, measurer, breakMeasurer, fieldTextStyle, highlighting, lineWrapping,
        animations.reflow, animations.folding, folds, inlineFoldText,
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
        state.addSpliceObserver(SplicePhase.Rows, layout.rowObserver)
        state.addBeforeRemoteObserver(layout.scrollAnchor.beforeRemoteChange)
        onDispose {
            state.removeSpliceObserver(SplicePhase.Rows, layout.rowObserver)
            state.removeBeforeRemoteObserver(layout.scrollAnchor.beforeRemoteChange)
        }
    }

    LaunchedEffect(layout) {
        snapshotFlow { state.version }.collect { currentOnTextLayout(layout) }
    }

    val currentRemoteCursors = rememberUpdatedState(remoteCursors)
    val glides = rememberRemoteCursorGlides(state, layout, animations.appearance, remoteCursors) {
        settledGeometry.visibleSize.width
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

    // text that others insert or remove in front of what the user looks at moves the view with it, in the same frame
    RemoteScrollAnchorEffect(
        layout = layout,
        enabled = keepViewOnRemoteChanges,
        anchor = {
            val visible = settledGeometry.visibleSize
            val view = Rect(offset, Size(visible.width.toFloat(), visible.height.toFloat()))
            remoteScrollAnchorIn(layout, state.selection.end, focused, view, textStartPadding)
        },
        scrollBy = { distance ->
            val before = offset
            offset = EditorGeometry.coerce(before + distance, maxOffset)
            offset - before
        },
    )

    // the text can get shorter and the zoom can change, the view must not stay beyond the end of the text
    LaunchedEffect(maxOffset) {
        val before = offset.y
        offset = EditorGeometry.coerce(offset, maxOffset)
        layout.onScrolledByReflow(offset.y - before)
    }

    val selection = state.selection

    // Follow the cursor while the user edits: when the selection moves or the editor changes its size.
    // Panning or remote changes of the text alone don't scroll, also not when they move the selection.
    LaunchedEffect(keepCursorVisible, focused, selection, totalSize, zoom, wrapWidth.value) {
        val follow = cursorFollower.shouldFollow(selection, focused, selection == state.selectionMovedByOthers)
        // a dragged handle scrolls the view itself
        if (!keepCursorVisible || !follow || totalSize == IntSize.Zero || touch.draggedHandle != null) {
            return@LaunchedEffect
        }
        offset = CursorVisibility.offsetFor(
            offset = offset,
            cursor = layout.getCursorRect(selection.end).translate(textStartPadding, 0f),
            viewport = settledGeometry.visibleSize,
            maxOffset = maxOffset,
        )
    }

    val viewport = remember(layout, textStartPadding) {
        EditorViewportAdapter(
            state = state,
            layout = layout,
            textStartPadding = textStartPadding,
            offsetState = offsetState,
            zoomState = zoomState,
            scope = viewScope,
            geometry = { settledGeometry },
            size = { totalSize },
            maxOffsetOf = { maxOffset },
            zoomRangeOf = { currentZoomRange },
            animatesOf = { currentAnimations.scrolling },
            foldsOf = { currentFolds },
            onMoved = cursorFollower::onUserMovedView,
        )
    }
    val viewportReady = totalSize != IntSize.Zero
    DisposableEffect(viewState, viewport, viewportReady) {
        if (viewportReady) viewState.viewport = viewport
        onDispose { if (viewState.viewport === viewport) viewState.viewport = null }
    }
    DisposableEffect(viewState) {
        onDispose {
            viewState.interrupt()
            viewState.gestureMoving = false
            viewState.handleMoving = false
            viewState.scrollbarMoving = false
        }
    }
    // before the size is known the view cannot be at its place, and a jump that waits for it must not show anything
    // else
    val contentVisibility = if (viewportReady && !viewState.jumpPending) Modifier else Modifier.alpha(0f)

    /** Zooms without fingers to zoom around, so the cursor or the selection stays in its place if it is visible */
    fun zoomAroundCursor(newZoom: Float) {
        if (newZoom != zoom) {
            viewState.interrupt()
            currentOnUserMovedView()
        }
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

    fun focusAndShowKeyboard() {
        focusRequester.requestFocus()
        if (!readOnly) keyboard?.show()
    }

    /** @return true if a column of the bar took the tap */
    fun tapGutter(position: Offset): Boolean {
        if (position.x >= geometry.gutterOnScreen) return false
        val lines = layout.lineCount
        val x = position.x / geometry.gutterScale + targetGutterWidth - geometry.gutterWidth
        val column = gutterColumnAt(columns, lines, x) ?: return false
        return column.onTap(layout.getLineForVerticalPosition(geometry.toText(position, offset).y))
    }

    val gestures = remember(state, layout, enabled, readOnly, viewState) {
        EditorTouchGestures(
            state = state,
            layout = layout,
            enabled = enabled,
            viewState = viewState,
            touch = touch,
            haptics = haptics,
            focus = ::focusAndShowKeyboard,
            toText = { geometry.toText(it, offset) },
            tapGutter = ::tapGutter,
            tapTrailingText = { layout.tapTrailingText(it, currentDecorations) },
        )
    }

    /** the point the handle at this text position points at, null outside the visible text */
    fun handleAnchor(position: Int): Offset? {
        val cursor = layout.localCursorRect(position)
        val point = geometry.toEditor(cursor.bottomCenter, offset)
        val visibleX = point.x >= geometry.gutterOnScreen && point.x <= totalSize.width
        return point.takeIf { visibleX && it.y >= 0f && it.y <= totalSize.height }
    }

    fun moveHandle(kind: HandleKind, target: Offset) {
        val word = touch.pressedWord
        if (word != null) {
            gestures.onSelectionDrag(target)
            return
        }
        val point = geometry.toText(target, offset)
        val position = layout.getOffsetForPosition(Offset(point.x, point.y - layout.lineHeight / 2))
        state.setSelection(HandleDrag.selectionFor(kind, position, state.selection))
    }

    HandleEdgeScrollEffect(
        touch = touch,
        viewState = viewState,
        area = { Size(totalSize.width.toFloat(), totalSize.height.toFloat()) },
        textLeft = { geometry.gutterOnScreen },
        scrolled = { distance ->
            EditorGeometry.coerce(offset + distance / zoom, maxOffset).takeIf { it != offset }
        },
        scrollTo = { offset = it },
        onUserMovedView = { currentOnUserMovedView() },
        moveHandle = ::moveHandle,
    )

    EditorTextMenuEffect(
        state = state,
        layout = layout,
        menu = menuActions,
        readOnly = readOnly,
        focused = focused,
        touch = touch,
        hasClipboardText = { clipboard.hasText() },
        coordinates = { editorCoordinates },
        toEditor = { geometry.toEditor(it, offset) },
        offset = offset,
        zoom = zoom,
    )

    val keys = remember(state, menuActions, keyboardCursor) { EditorKeys(state, menuActions, keyboardCursor) }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled || event.type != KeyEventType.KeyDown || !event.isFromTextKeyboard()) return false
        return keys.onKeyDown(
            event = event,
            editable = !readOnly,
            keyBindings = keyBindings,
            folds = folds,
            lines = { EditorTextLines(layout) },
            pageHeight = { totalSize.height / zoom },
            onCursorMoved = { touch.touchInput = false },
        ) { step ->
            when (step) {
                KeyZoom.In -> zoomAroundCursor((zoom * KEYBOARD_ZOOM_STEP).coerceAtMost(maxZoom))
                KeyZoom.Out -> zoomAroundCursor((zoom / KEYBOARD_ZOOM_STEP).coerceAtLeast(minZoom))
                KeyZoom.Reset -> zoomAroundCursor(1f.coerceIn(minZoom, maxZoom))
            }
        }
    }

    val hasSelection = !selection.collapsed
    val handlesWanted = focused && touch.touchInput
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
                val intercept = event.toKeyboardCommand(keyBindings) == KeyboardCommand.StartNewLine &&
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
            .editorSemantics(state, enabled, readOnly, ::focusAndShowKeyboard)
            .focusable(enabled = enabled),
    ) {
        EditorGutter(
            modifier = Modifier
                .zIndex(1f)
                .matchParentSize()
                .then(contentVisibility),
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
                .then(contentVisibility)
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
            onFlingChanged = { touch.flinging = it },
            contentStart = { geometry.gutterOnScreen(it) },
            onMovingChanged = { moving ->
                viewState.gestureMoving = moving
                if (moving) {
                    viewState.interrupt()
                    currentOnUserMovedView()
                }
            },
            flingStops = flingStops,
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
                if (search != null) {
                    SearchLayer(
                        layout = layout,
                        search = search,
                        window = drawWindow,
                        matchColor = shownColors.searchMatch,
                        currentColor = shownColors.currentSearchMatch,
                    )
                }
                SelectionLayer(
                    layout = layout,
                    selection = { state.selection.takeIf { focused } },
                    window = drawWindow,
                )
                if (remoteCursors.isNotEmpty() || glides.hasCursors) {
                    RemoteSelectionLayer(layout, currentRemoteCursors, glides, drawWindow)
                }
                VisibleLines(
                    layout = layout,
                    window = drawWindow,
                    decorations = decorations,
                    foldBoxColor = shownColors.inlineFoldBackground,
                    appearSlide = appearSlide,
                )
                if (focused && enabled && !readOnly && selection.collapsed) {
                    EditorCursor(
                        state = state,
                        layout = layout,
                        color = shownColors.cursor,
                        blinking = windowInfo.isWindowFocused,
                    )
                }
                if (remoteCursors.isNotEmpty() || glides.hasCursors) {
                    RemoteCaretLayer(currentRemoteCursors, glides, drawWindow)
                    if (remoteCursorLabel != null) {
                        Box(modifier = Modifier.placedAt { IntRect(0, 0, totalSize.width, totalSize.height) }) {
                            RemoteCursorLabels(
                                cursors = remoteCursors,
                                glides = glides,
                                visibleRight = { offset.x - textStartPadding + geometry.visibleSize.width },
                                label = remoteCursorLabel,
                            )
                        }
                    }
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

        if (scrollbars) {
            Box(modifier = Modifier.zIndex(2f).matchParentSize().then(contentVisibility)) {
                EditorScrollbars(
                    color = shownColors.scrollbar,
                    style = scrollbarStyle,
                    geometry = { geometry },
                    offset = { offset },
                    maxOffset = { maxOffset },
                    held = { viewState.isUserMovingView },
                    onDragStart = {
                        flingStops++
                        viewState.scrollbarMoving = true
                        viewState.interrupt()
                        cursorFollower.onUserMovedView()
                        currentOnUserMovedView()
                    },
                    onDrag = {
                        cursorFollower.onUserMovedView()
                        offset = EditorGeometry.coerce(it, maxOffset)
                    },
                    onDragEnd = { viewState.scrollbarMoving = false },
                )
            }
        }

        // above the scrollbars, a handle at the edge must stay reachable
        Box(modifier = Modifier.zIndex(3f).matchParentSize()) {
            handles.forEach { kind ->
                key(kind) {
                    SelectionHandle(
                        kind = kind,
                        color = shownColors.handle,
                        anchor = {
                            handleAnchor(
                                if (kind == HandleKind.SelectionEnd) state.selection.max
                                else state.selection.min
                            )
                        },
                        editorCoordinates = { editorCoordinates },
                        onDrag = {
                            touch.dragTarget = it
                            touch.draggedHandle = kind
                            moveHandle(kind, it)
                        },
                        onDragEnd = {
                            touch.draggedHandle = null
                            touch.dragTarget = null
                        },
                        onTap = {
                            if (kind == HandleKind.Cursor) touch.cursorMenuRequested = !touch.cursorMenuRequested
                        },
                    )
                }
            }
        }
    }
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
    val state = remember {
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
        KodeEditorState(initialText)
    }

    val languageRuleBook by remember {
        mutableStateOf(dummyData.dummyRuleBook)
    }
    val colorScheme by remember {
        mutableStateOf(dummyData.colorScheme)
    }

    KodeEditor(
        modifier = Modifier.fillMaxSize(),
        state = state,
        languageRuleBook = languageRuleBook,
        colorScheme = colorScheme,
    )
}
