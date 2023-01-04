package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun ZoomLayoutPreview() {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(2f) }

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

@Composable
fun ZoomLayout(
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    minZoom: Float = 0.01f,
    maxZoom: Float = 10f,
    offset: Offset = Offset.Zero,
    onOffsetChanged: (Offset) -> Unit,
    onZoomChanged: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, gestureRotate ->

                    val oldScale = zoom
                    val newScale = zoom * gestureZoom

                    // For natural zooming and rotating, the centroid of the gesture should
                    // be the fixed point where zooming and rotating occurs.
                    // We compute where the centroid was (in the pre-transformed coordinate
                    // space), and then compute where it will be after this delta.
                    // We then compute what the new offset should be to keep the centroid
                    // visually stationary for rotating and zooming, and also apply the pan.
                    val tOffset = (offset + centroid / oldScale) - (centroid / newScale + pan / oldScale)

                    if (offset != tOffset) {
                        onOffsetChanged(tOffset)
                    }

                    val newZoom = (newScale).coerceIn(minZoom, maxZoom)
                    if (zoom != newZoom) {
                        onZoomChanged(newZoom)
                    }
                }
            }
            .graphicsLayer(
                transformOrigin = TransformOrigin(0f, 0f),
                scaleX = zoom, scaleY = zoom,
                translationX = -offset.x * zoom,
                translationY = -offset.y * zoom,
            )
            .then(modifier),
    ) {
        content()
    }
}