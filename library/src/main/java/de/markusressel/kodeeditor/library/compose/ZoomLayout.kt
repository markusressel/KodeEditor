package de.markusressel.kodeeditor.library.compose

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Pans and zooms its content with touch gestures.
 *
 * @param modifier compose modifiers
 * @param zoom the current zoom factor
 * @param minZoom the smallest zoom factor a gesture can reach
 * @param maxZoom the largest zoom factor a gesture can reach
 * @param offset the current offset, the point of the content shown in the top left corner
 * @param onOffsetChanged called with the new offset
 * @param onZoomChanged called with the new zoom factor, before [onOffsetChanged] of the same gesture event
 * @param coerceOffset the offset that can be reached for a wanted offset, e.g. the end of the content. A movement
 * beyond it is lost, so moving back has an effect right away
 * @param onFlingChanged called when the content starts or stops moving on after the fingers left
 * @param contentStart where the content starts at a zoom factor, for a bar in front of it that does not zoom with it
 * @param onMovingChanged called with true at the first movement of each gesture, and with false when the view has come
 * to rest and no finger holds it anymore, which includes the fling after the gesture
 * @param content the content to pan and zoom
 */
@Composable
fun ZoomLayout(
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    minZoom: Float = 0.25f,
    maxZoom: Float = 10f,
    offset: Offset = Offset.Zero,
    onOffsetChanged: (Offset) -> Unit,
    onZoomChanged: (Float) -> Unit,
    coerceOffset: (Offset) -> Offset = { it },
    onFlingChanged: (Boolean) -> Unit = {},
    contentStart: (zoom: Float) -> Float = { 0f },
    onMovingChanged: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    // The gesture detector outlives recompositions, so it must read the current values. Several events can arrive
    // before the next recomposition, so it also keeps its own result until then, or it would lose their movement.
    var currentZoom by remember { mutableFloatStateOf(zoom) }.apply { floatValue = zoom }
    var currentOffset by remember { mutableStateOf(offset) }.apply { value = offset }
    val currentOnOffsetChanged by rememberUpdatedState(onOffsetChanged)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val currentCoerceOffset by rememberUpdatedState(coerceOffset)
    val currentOnFlingChanged by rememberUpdatedState(onFlingChanged)
    val currentContentStart by rememberUpdatedState(contentStart)
    val currentOnMovingChanged by rememberUpdatedState(onMovingChanged)
    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    // only the gesture detector and the fling use these, there is nothing to draw from them
    val movement = remember { GestureMovement { currentOnMovingChanged(it) } }

    Box(
        modifier = Modifier
            .pointerInput(minZoom, maxZoom) {
                val decay = splineBasedDecay<Offset>(this)
                val minimumSpeed = MIN_FLING_SPEED.toPx()
                detectPanAndZoom(
                    onStart = {
                        movement.onFingerDown()
                        flingJob?.cancel()
                    },
                    onFirstMove = { movement.onGestureMoved() },
                    onGesture = { centroid, pan, gestureZoom ->
                        val oldZoom = currentZoom
                        val oldOffset = currentOffset
                        val newZoom = (oldZoom * gestureZoom).coerceIn(minZoom, maxZoom)
                        val newOffset = currentCoerceOffset(
                            zoomedOffset(
                                offset = oldOffset,
                                oldZoom = oldZoom,
                                newZoom = newZoom,
                                centroid = centroid,
                                pan = pan,
                                oldStart = currentContentStart(oldZoom),
                                newStart = currentContentStart(newZoom),
                            ),
                        )
                        currentZoom = newZoom
                        currentOffset = newOffset
                        if (newZoom != oldZoom) currentOnZoomChanged(newZoom)
                        if (newOffset != oldOffset) currentOnOffsetChanged(newOffset)
                    },
                    onEnd = { velocity ->
                        if (velocity.getDistance() < minimumSpeed) return@detectPanAndZoom
                        movement.onFlingStarted()
                        flingJob = scope.launch {
                            currentOnFlingChanged(true)
                            try {
                                fling(
                                    velocity = -velocity / currentZoom,
                                    decay = decay,
                                    current = { currentOffset },
                                    coerce = { currentCoerceOffset(it) },
                                    onMove = {
                                        currentOffset = it
                                        currentOnOffsetChanged(it)
                                    },
                                )
                            } finally {
                                currentOnFlingChanged(false)
                                movement.onFlingEnded()
                            }
                        }
                    },
                    onFinish = { movement.onFingersGone() },
                )
            }
            .graphicsLayer(
                transformOrigin = TransformOrigin(0f, 0f),
                scaleX = zoom,
                scaleY = zoom,
                translationX = contentStart(zoom) - offset.x * zoom,
                translationY = -offset.y * zoom,
            )
            .then(modifier),
    ) {
        content()
    }
}

private val MIN_FLING_SPEED = 100.dp

/**
 * Tells when the user starts and stops moving the view. A finger that stops a fling keeps holding the view, so that is
 * still moving, until the finger leaves.
 *
 * @param onMovingChanged see [ZoomLayout]
 */
internal class GestureMovement(private val onMovingChanged: (Boolean) -> Unit) {
    private var moving = false
    private var fingerDown = false

    /** the flings that have not ended yet, a new one can start before a stopped one has noticed */
    private var flings = 0

    fun onFingerDown() {
        fingerDown = true
    }

    fun onGestureMoved() {
        moving = true
        onMovingChanged(true)
    }

    fun onFlingStarted() {
        flings++
    }

    fun onFlingEnded() {
        flings--
        endIfAtRest()
    }

    fun onFingersGone() {
        fingerDown = false
        endIfAtRest()
    }

    private fun endIfAtRest() {
        if (moving && !fingerDown && flings == 0) {
            moving = false
            onMovingChanged(false)
        }
    }
}

/**
 * Lets the content move on after the fingers left, slowing down like in a list. It ends at the end of the content,
 * and when something else moves the content, like the editor following its cursor.
 */
private suspend fun fling(
    velocity: Offset,
    decay: DecayAnimationSpec<Offset>,
    current: () -> Offset,
    coerce: (Offset) -> Offset,
    onMove: (Offset) -> Unit,
) {
    var last = current()
    AnimationState(Offset.VectorConverter, last, velocity).animateDecay(decay) {
        if (current() != last) {
            cancelAnimation()
            return@animateDecay
        }
        val next = coerce(value)
        if (next != value && next == last) cancelAnimation()
        if (next != last) {
            last = next
            onMove(next)
        }
    }
}

/**
 * Like `detectTransformGestures`, which does not tell when a gesture ends and how fast it was.
 *
 * @param onStart a finger went down
 * @param onFirstMove the fingers moved past the touch slop, once per gesture
 * @param onGesture the fingers moved, with their center, how far they moved and the change of the zoom
 * @param onEnd the last finger left after a movement, with the speed it had in pixels per second. Not called when
 * someone else took the gesture over.
 * @param onFinish the gesture is over for any reason, after [onEnd]
 */
private suspend fun PointerInputScope.detectPanAndZoom(
    onStart: () -> Unit,
    onFirstMove: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onEnd: (velocity: Offset) -> Unit,
    onFinish: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onStart()
        try {
            var zoom = 1f
            var pan = Offset.Zero
            var pastTouchSlop = false
            var path = Offset.Zero
            var pointers = 1
            var tracker = VelocityTracker()
            var canceled: Boolean
            do {
                val event = awaitPointerEvent()
                canceled = event.changes.any { it.isConsumed }
                if (!canceled) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    if (!pastTouchSlop) {
                        zoom *= zoomChange
                        pan += panChange
                        val zoomMotion = abs(1 - zoom) * event.calculateCentroidSize(useCurrent = false)
                        pastTouchSlop = zoomMotion > viewConfiguration.touchSlop ||
                            pan.getDistance() > viewConfiguration.touchSlop
                        if (pastTouchSlop) onFirstMove()
                    }
                    if (pastTouchSlop) {
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                    // only the movement of a pan counts for the speed, not that of a pinch or of a finger that left
                    val pressed = event.changes.count { it.pressed }
                    if (pressed > 0 && pressed != pointers || zoomChange != 1f) tracker = VelocityTracker()
                    pointers = pressed
                    path += panChange
                    if (pressed > 0) tracker.addPosition(event.changes.first().uptimeMillis, path)
                }
            } while (!canceled && event.changes.any { it.pressed })
            if (!canceled && pastTouchSlop) {
                val maximum = viewConfiguration.maximumFlingVelocity
                val velocity = tracker.calculateVelocity(Velocity(maximum, maximum))
                onEnd(Offset(velocity.x, velocity.y))
            }
        } finally {
            onFinish()
        }
    }
}

/**
 * The offset that keeps the point of the content under [centroid] under the fingers, which moved by [pan].
 */
internal fun zoomedOffset(
    offset: Offset,
    oldZoom: Float,
    newZoom: Float,
    centroid: Offset,
    pan: Offset,
    oldStart: Float = 0f,
    newStart: Float = 0f,
): Offset {
    return offset + (centroid - Offset(oldStart, 0f)) / oldZoom - (centroid + pan - Offset(newStart, 0f)) / newZoom
}

@Preview
@Composable
private fun ZoomLayoutPreview() {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(2f) }

    ZoomLayout(
        modifier = Modifier
            .padding(16.dp)
            .background(Color.White),
        zoom = zoom,
        offset = offset,
        onOffsetChanged = { offset = it },
        onZoomChanged = { zoom = it },
    ) {
        Column(modifier = Modifier) {
            for (i in 1..10) {
                Row(modifier = Modifier) {
                    for (j in 1..10) {
                        val k = (i + j) % 2
                        Surface(
                            modifier = Modifier.size(20.dp), color = when (k) {
                            1 -> Color.Black
                            else -> Color.White
                        }) {
                        }
                    }
                }
            }
        }
    }
}
