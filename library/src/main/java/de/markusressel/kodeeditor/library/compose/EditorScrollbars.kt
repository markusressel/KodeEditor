package de.markusressel.kodeeditor.library.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

internal const val VERTICAL_SCROLLBAR_TAG = "kodeeditor-vertical-scrollbar"
internal const val HORIZONTAL_SCROLLBAR_TAG = "kodeeditor-horizontal-scrollbar"

private val SCROLLBAR_THICKNESS = 4.dp
private val SCROLLBAR_MARGIN = 2.dp

/** the size of the touch area across the bar, the bar itself is too thin to hit */
private val SCROLLBAR_TOUCH_SIZE = 24.dp
private val SCROLLBAR_MIN_THUMB = 32.dp
internal const val SCROLLBAR_HIDE_DELAY_MS = 1000L
private const val SCROLLBAR_FADE_MS = 150

/**
 * Where the thumb of a scrollbar is on its track, along its axis. The track is in pixels on screen, the offsets are in
 * text coordinates.
 */
internal object ScrollbarMath {

    /** @return the length of the thumb, 0 when there is nothing to scroll */
    fun thumbLength(track: Float, visible: Float, maxOffset: Float, minLength: Float): Float {
        if (maxOffset <= 0f || visible <= 0f || track <= 0f) return 0f
        return (track * visible / (visible + maxOffset)).coerceIn(minOf(minLength, track), track)
    }

    /** @return the start of the thumb on the track */
    fun thumbStart(track: Float, length: Float, offset: Float, maxOffset: Float): Float {
        if (maxOffset <= 0f) return 0f
        return (track - length) * (offset / maxOffset).coerceIn(0f, 1f)
    }

    /** @return the offset after the thumb moved by [moved] from where it was at [startOffset] */
    fun offsetFor(startOffset: Float, moved: Float, track: Float, length: Float, maxOffset: Float): Float {
        val room = track - length
        if (room <= 0f) return startOffset
        return (startOffset + moved * maxOffset / room).coerceIn(0f, maxOffset)
    }
}

private enum class ScrollbarAxis(val tag: String) {
    Vertical(VERTICAL_SCROLLBAR_TAG),
    Horizontal(HORIZONTAL_SCROLLBAR_TAG),
}

/** a thumb on screen: where its track starts, how long the track is, and the place and length of the thumb on it */
private class ThumbPlacement(val trackStart: Float, val track: Float, val start: Float, val length: Float)

/**
 * The scrollbars of the editor. They show up while the view moves and fade out after it stopped. While they are shown,
 * their thumbs can be dragged.
 *
 * They are not scaled with the text, so this must be outside the zoom layout, at the size of the editor.
 *
 * @param color the color of the thumbs
 * @param geometry where the text is in the editor
 * @param offset the scroll offset in text coordinates
 * @param maxOffset the largest scroll offset
 * @param onDragStart a thumb is about to be dragged
 * @param onDrag called with the new offset while a thumb is dragged
 * @param onDragEnd the thumb was released
 */
@Composable
internal fun BoxScope.EditorScrollbars(
    color: Color,
    geometry: () -> EditorGeometry,
    offset: () -> Offset,
    maxOffset: () -> Offset,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentGeometry by rememberUpdatedState(geometry)
    val currentOffset by rememberUpdatedState(offset)
    val currentMaxOffset by rememberUpdatedState(maxOffset)
    var moved by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // a released thumb stays for a moment too, even when it did not move at the end
    var releases by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        snapshotFlow { Triple(currentOffset(), currentGeometry().zoom, releases) }
            .drop(1)
            .collectLatest {
                moved = true
                delay(SCROLLBAR_HIDE_DELAY_MS)
                moved = false
            }
    }
    val shown = moved || dragging
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(SCROLLBAR_FADE_MS),
        label = "scrollbarAlpha",
    )

    var container by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .onPlaced { container = it }
            .graphicsLayer { this.alpha = alpha },
    ) {
        ScrollbarAxis.entries.forEach { axis ->
            key(axis) {
                ScrollbarThumb(
                    axis = axis,
                    color = color,
                    shown = { shown },
                    placement = { size ->
                        placement(axis, size, currentGeometry(), currentOffset(), currentMaxOffset())
                    },
                    container = { container },
                    onDragStart = {
                        dragging = true
                        onDragStart()
                    },
                    onDrag = { startOffset, along, thumb ->
                        val max = currentMaxOffset()
                        val current = currentOffset()
                        onDrag(
                            when (axis) {
                                ScrollbarAxis.Vertical -> current.copy(
                                    y = ScrollbarMath.offsetFor(startOffset.y, along, thumb.track, thumb.length, max.y),
                                )

                                ScrollbarAxis.Horizontal -> current.copy(
                                    x = ScrollbarMath.offsetFor(startOffset.x, along, thumb.track, thumb.length, max.x),
                                )
                            },
                        )
                    },
                    onDragEnd = {
                        dragging = false
                        releases++
                        onDragEnd()
                    },
                    offset = { currentOffset() },
                )
            }
        }
    }
}

/** both bars leave the corner at the bottom right to each other when both are there */
private fun Density.placement(
    axis: ScrollbarAxis,
    size: IntSize,
    geometry: EditorGeometry,
    offset: Offset,
    maxOffset: Offset,
): ThumbPlacement? {
    val margin = SCROLLBAR_MARGIN.toPx()
    val corner = SCROLLBAR_THICKNESS.toPx() + 2 * margin
    val minThumb = SCROLLBAR_MIN_THUMB.toPx()
    val visible = geometry.visibleSize
    return when (axis) {
        ScrollbarAxis.Vertical -> {
            val trackStart = margin
            val track = size.height - trackStart - margin - if (maxOffset.x > 0f) corner else 0f
            val length = ScrollbarMath.thumbLength(track, visible.height, maxOffset.y, minThumb)
            if (length <= 0f) return null
            ThumbPlacement(trackStart, track, ScrollbarMath.thumbStart(track, length, offset.y, maxOffset.y), length)
        }

        ScrollbarAxis.Horizontal -> {
            val trackStart = geometry.gutterOnScreen + margin
            val track = size.width - trackStart - margin - if (maxOffset.y > 0f) corner else 0f
            val length = ScrollbarMath.thumbLength(track, visible.width, maxOffset.x, minThumb)
            if (length <= 0f) return null
            ThumbPlacement(trackStart, track, ScrollbarMath.thumbStart(track, length, offset.x, maxOffset.x), length)
        }
    }
}

/**
 * One thumb, placed without recomposing. It is out of the way of the text while it is hidden, so it does not take
 * touches then.
 */
@Composable
private fun ScrollbarThumb(
    axis: ScrollbarAxis,
    color: Color,
    shown: () -> Boolean,
    placement: Density.(IntSize) -> ThumbPlacement?,
    container: () -> LayoutCoordinates?,
    onDragStart: () -> Unit,
    onDrag: (startOffset: Offset, along: Float, thumb: ThumbPlacement) -> Unit,
    onDragEnd: () -> Unit,
    offset: () -> Offset,
) {
    var own by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentPlacement by rememberUpdatedState(placement)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOffset by rememberUpdatedState(offset)
    var lastSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val size = IntSize(constraints.maxWidth, constraints.maxHeight)
                lastSize = size
                val thumb = placement(size).takeIf { shown() }
                val touch = SCROLLBAR_TOUCH_SIZE.roundToPx()
                val length = thumb?.length?.roundToInt()?.coerceAtLeast(1) ?: 1
                val placeable = when (axis) {
                    ScrollbarAxis.Vertical -> measurable.measure(Constraints.fixed(touch, length))
                    ScrollbarAxis.Horizontal -> measurable.measure(Constraints.fixed(length, touch))
                }
                layout(size.width, size.height) {
                    when {
                        thumb == null -> placeable.place(-2 * touch - length, -2 * touch - length)
                        axis == ScrollbarAxis.Vertical ->
                            placeable.place(size.width - touch, (thumb.trackStart + thumb.start).roundToInt())

                        else -> placeable.place((thumb.trackStart + thumb.start).roundToInt(), size.height - touch)
                    }
                }
            }
            .testTag(axis.tag)
            .onPlaced { own = it }
            .pointerInput(axis) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val thumb = currentPlacement(this, lastSize) ?: return@awaitEachGesture
                    down.consume()
                    val start = containerPosition(own, container(), down) ?: return@awaitEachGesture
                    // the thumb keeps its track for the whole drag
                    val startOffset = currentOffset()
                    currentOnDragStart()
                    try {
                        var change = down
                        while (change.pressed) {
                            val event = awaitPointerEvent()
                            change = event.changes.firstOrNull { it.id == down.id } ?: break
                            event.changes.forEach { it.consume() }
                            val position = containerPosition(own, container(), change) ?: continue
                            val along = when (axis) {
                                ScrollbarAxis.Vertical -> position.y - start.y
                                ScrollbarAxis.Horizontal -> position.x - start.x
                            }
                            currentOnDrag(startOffset, along, thumb)
                        }
                    } finally {
                        currentOnDragEnd()
                    }
                }
            }
            .drawBehind {
                val margin = SCROLLBAR_MARGIN.toPx()
                val thickness = SCROLLBAR_THICKNESS.toPx()
                val radius = CornerRadius(thickness / 2)
                when (axis) {
                    ScrollbarAxis.Vertical -> drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width - margin - thickness, 0f),
                        size = Size(thickness, size.height),
                        cornerRadius = radius,
                    )

                    ScrollbarAxis.Horizontal -> drawRoundRect(
                        color = color,
                        topLeft = Offset(0f, size.height - margin - thickness),
                        size = Size(size.width, thickness),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

/** The thumb itself moves while it is dragged, so the position must not be taken relative to it */
private fun containerPosition(
    own: LayoutCoordinates?,
    container: LayoutCoordinates?,
    change: PointerInputChange,
): Offset? {
    if (own == null || container == null || !own.isAttached || !container.isAttached) return null
    return container.localPositionOf(own, change.position)
}

@Preview
@Composable
private fun EditorScrollbarsPreview() {
    var offset by remember { mutableStateOf(Offset(0f, 300f)) }
    Box(modifier = Modifier.size(200.dp)) {
        EditorScrollbars(
            color = Color.Gray,
            geometry = { EditorGeometry(IntSize(200, 200), 0, 0f, 1f) },
            offset = { offset },
            maxOffset = { Offset(400f, 1000f) },
            onDragStart = {},
            onDrag = { offset = it },
            onDragEnd = {},
        )
    }
}
