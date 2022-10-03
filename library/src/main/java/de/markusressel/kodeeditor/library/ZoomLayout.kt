package de.markusressel.kodeeditor.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun ZoomLayoutPreview() {
    ZoomLayout(
        modifier = Modifier
            .padding(16.dp)
            .background(Color.White)
    ) {
        Column(modifier = Modifier) {
            for (i in 1..10) {
                Row(modifier = Modifier) {
                    for (j in 1..10) {
                        val k = j + i % 2
                        Surface(
                            modifier = Modifier.size(20.dp), color = when {
                            k % 2 == 0 -> Color.Black
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
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.wrapContentSize(
            align = Alignment.TopStart,
            unbounded = true
        )
    ) {
        val configuration = LocalConfiguration.current

        var offset by remember { mutableStateOf(Offset.Zero) }
        var zoom by remember { mutableStateOf(1f) }

        Box(
            modifier = Modifier
                .wrapContentSize(
                    align = Alignment.TopStart,
                    unbounded = true
                )
                .background(Color.White)
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
                        offset = (offset + centroid / oldScale) - (centroid / newScale + pan / oldScale)

                        offset = Offset(
                            x = offset.x.coerceIn(0f, (size.width.toFloat() - (configuration.screenWidthDp.dp.toPx() / newScale)).coerceAtLeast(0f)),
                            y = offset.y.coerceIn(0f, (size.height.toFloat() - (configuration.screenHeightDp.dp.toPx() / newScale)).coerceAtLeast(0f)),
                        )

                        zoom = (newScale).coerceIn(0.1f, 5f)
                    }
                }
                .graphicsLayer(
                    transformOrigin = TransformOrigin(0f, 0f),
                    scaleX = zoom, scaleY = zoom,
                    translationX = -offset.x * zoom,
                    translationY = -offset.y * zoom,
                ),
        ) {
            content()
        }
    }
}