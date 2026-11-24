package de.markusressel.kodeeditor.library.compose.view

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntSize
import de.markusressel.kodeeditor.library.compose.fold.EditorFolds
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import kotlinx.coroutines.CoroutineScope

/** where the bottom edge of the view counts as the row above, so a view that ends exactly at a row does not show it */
private const val VISIBLE_RANGE_EDGE = 0.01f

/**
 * The view of one editor as [KodeEditorViewState] sees it: where it is, what it shows, and how to move it there.
 *
 * @param state the text
 * @param layout the layout of the text
 * @param textStartPadding the room in front of the text, in pixels
 * @param offsetState the top left corner of the view in the text
 * @param zoomState the zoom factor
 * @param scope where the animations of the view state run
 * @param geometry the room of the text, with the bar at the width it animates to
 * @param size the size of the editor
 * @param maxOffsetOf the largest offset, at the end of the text
 * @param zoomRangeOf the zoom factors the user can reach
 * @param animatesOf whether the view state animates its moves
 * @param foldsOf the folds of the editor, null if it does not fold
 * @param onMoved called when the view moved like the user moved it, so the cursor is no longer followed
 */
internal class EditorViewportAdapter(
    private val state: KodeEditorState,
    private val layout: EditorLineLayout,
    private val textStartPadding: Float,
    private val offsetState: MutableState<Offset>,
    private val zoomState: MutableFloatState,
    override val scope: CoroutineScope,
    private val geometry: () -> EditorGeometry,
    private val size: () -> IntSize,
    private val maxOffsetOf: () -> Offset,
    private val zoomRangeOf: () -> ClosedFloatingPointRange<Float>,
    private val animatesOf: () -> Boolean,
    private val foldsOf: () -> EditorFolds?,
    private val onMoved: () -> Unit,
) : EditorViewport {

    private val shownRange = derivedStateOf {
        val height = geometry().visibleSize.height
        if (height <= 0f) return@derivedStateOf null
        val offset = offsetState.value
        // the bottom edge of the view is the top of the next row when it is exactly at the end of a row
        val lastY = offset.y + height - VISIBLE_RANGE_EDGE
        TextRange(layout.startOfRow(layout.rowAt(offset.y)), layout.endOfRow(layout.rowAt(lastY)))
    }

    override val offset: Offset get() = offsetState.value
    override val visibleSize: Size get() = geometry().visibleSize
    override val animates: Boolean get() = animatesOf()
    override val visibleRange: TextRange? get() = shownRange.value
    override val maxOffset: Offset get() = maxOffsetOf()
    override val zoom: Float get() = zoomState.floatValue
    override val zoomRange get() = zoomRangeOf()
    override val folds get() = foldsOf()

    override fun moveTo(offset: Offset) {
        // the view stays there when the editor changes its size, as if the user moved it
        onMoved()
        offsetState.value = EditorGeometry.coerce(offset, maxOffset)
    }

    private fun rectOf(position: Int): Rect {
        val inText = position.coerceIn(0, state.rawText.length)
        return layout.getCursorRect(inText).translate(textStartPadding, 0f)
    }

    override fun offsetShowing(position: Int) = CursorVisibility.offsetFor(
        offset = offset,
        cursor = rectOf(position),
        viewport = visibleSize,
        maxOffset = maxOffset,
    )

    override fun offsetCentering(start: Int, end: Int) = CursorVisibility.centeredOffsetFor(
        offset = offset,
        top = rectOf(minOf(start, end)).top,
        bottom = rectOf(maxOf(start, end)).bottom,
        viewport = visibleSize,
        maxOffset = maxOffset,
    )

    override fun zoomTo(zoom: Float) {
        val oldZoom = zoomState.floatValue
        val newZoom = zoom.coerceIn(zoomRange)
        if (newZoom == oldZoom) return
        val size = size()
        val start = geometry().gutterOnScreen(oldZoom)
        // the middle of the text, right of the bar
        val middle = Offset(start + (size.width - start) / 2f, size.height / 2f)
        val moved = zoomedOffset(
            offset = offsetState.value,
            oldZoom = oldZoom,
            newZoom = newZoom,
            centroid = middle,
            pan = Offset.Zero,
            oldStart = start,
            newStart = geometry().gutterOnScreen(newZoom),
        )
        onMoved()
        offsetState.value = Offset(moved.x.coerceAtLeast(0f), moved.y.coerceAtLeast(0f))
        zoomState.floatValue = newZoom
    }

    override fun offsetPutting(line: Int, fraction: Float, y: Float): Offset {
        val shown = line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
        val top = layout.getLineTop(shown)
        val bottom = layout.getLineBottom(shown)
        val place = top + (bottom - top) * fraction.coerceIn(0f, 1f)
        return EditorGeometry.coerce(offset.copy(y = place - y), maxOffset)
    }

    override fun lineAt(y: Float): LinePosition {
        val line = layout.getLineForVerticalPosition(y)
        val top = layout.getLineTop(line)
        val height = layout.getLineBottom(line) - top
        val fraction = if (height > 0f) ((y - top) / height).coerceIn(0f, 1f) else 0f
        return LinePosition(line, fraction)
    }

    override fun isVisible(position: Int): Boolean {
        val visible = visibleSize
        val rect = rectOf(position)
        val inRows = rect.top >= offset.y && rect.bottom <= offset.y + visible.height
        return inRows && rect.left >= offset.x && rect.left <= offset.x + visible.width
    }
}
