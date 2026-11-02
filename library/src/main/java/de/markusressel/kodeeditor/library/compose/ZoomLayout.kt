package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
    content: @Composable BoxScope.() -> Unit,
) {
    // The gesture detector outlives recompositions, so it must read the current values. Several events can arrive
    // before the next recomposition, so it also keeps its own result until then, or it would lose their movement.
    var currentZoom by remember { mutableFloatStateOf(zoom) }.apply { floatValue = zoom }
    var currentOffset by remember { mutableStateOf(offset) }.apply { value = offset }
    val currentOnOffsetChanged by rememberUpdatedState(onOffsetChanged)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    Box(
        modifier = Modifier
            .pointerInput(minZoom, maxZoom) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val oldZoom = currentZoom
                    val oldOffset = currentOffset
                    val newZoom = (oldZoom * gestureZoom).coerceIn(minZoom, maxZoom)
                    val newOffset = zoomedOffset(oldOffset, oldZoom, newZoom, centroid, pan)
                    currentZoom = newZoom
                    currentOffset = newOffset
                    if (newZoom != oldZoom) currentOnZoomChanged(newZoom)
                    if (newOffset != oldOffset) currentOnOffsetChanged(newOffset)
                }
            }
            .graphicsLayer(
                transformOrigin = TransformOrigin(0f, 0f),
                scaleX = zoom,
                scaleY = zoom,
                translationX = -offset.x * zoom,
                translationY = -offset.y * zoom,
            )
            .then(modifier),
    ) {
        content()
    }
}

/**
 * The offset that keeps the point of the content under [centroid] under the fingers, which moved by [pan].
 */
internal fun zoomedOffset(offset: Offset, oldZoom: Float, newZoom: Float, centroid: Offset, pan: Offset): Offset {
    return offset + centroid / oldZoom - (centroid + pan) / newZoom
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
