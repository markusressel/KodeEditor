package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp

private val LINE_NUMBER_PADDING = 4.dp

/**
 * @param lineCount the number of lines of the document
 * @param textStyle the style of the line numbers
 * @return the width of the line numbers in pixels at zoom 1
 */
@Composable
internal fun rememberLineNumbersWidth(lineCount: Int, textStyle: TextStyle): Int {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val padding = LocalDensity.current.run { LINE_NUMBER_PADDING.roundToPx() }
    val digits = lineCount.toString().length
    return remember(digits, textStyle, padding) {
        measurer.measure("9".repeat(digits), style = textStyle, softWrap = false).size.width + 2 * padding
    }
}

/**
 * Draws the numbers of the lines within [window] next to the text. They scale with the [zoom] and follow the text
 * vertically.
 *
 * @param modifier compose modifiers
 * @param layout the text layout that decides where the lines are
 * @param width the width of the line numbers in pixels at zoom 1
 * @param textStyle the style of the line numbers
 * @param backgroundColor the color behind the line numbers
 * @param window the part of the document to record for drawing, in text coordinates
 * @param zoom the current zoom factor
 * @param offsetY the current vertical scroll offset
 */
@Composable
internal fun LineNumbers(
    modifier: Modifier = Modifier,
    layout: TextLayoutResult?,
    width: Int,
    textStyle: TextStyle,
    backgroundColor: Color,
    window: State<IntRect>,
    zoom: () -> Float,
    offsetY: () -> Float,
) {
    val measurer = rememberTextMeasurer(cacheSize = LINE_LAYOUT_CACHE_SIZE)
    val density = LocalDensity.current
    val padding = density.run { LINE_NUMBER_PADDING.toPx() }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(density.run { width.toDp() })
                .fillMaxHeight()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom()
                }
                .background(backgroundColor),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom()
                    scaleY = zoom()
                    translationY = -offsetY() * zoom()
                },
        ) {
            if (layout != null) {
                Box(
                    modifier = Modifier
                        .placedAt { window.value.let { IntRect(0, it.top, width, it.bottom) } }
                        .graphicsLayer {}
                        .drawBehind {
                            val area = window.value
                            val first = layout.getLineForVerticalPosition(area.top.toFloat())
                            val last = layout.getLineForVerticalPosition(area.bottom.toFloat())
                            for (line in first..last) {
                                val number = measurer.measure("${line + 1}", style = textStyle, softWrap = false)
                                drawText(
                                    textLayoutResult = number,
                                    topLeft = Offset(
                                        x = width - padding - number.size.width,
                                        y = layout.getLineBaseline(line) - number.firstBaseline - area.top,
                                    ),
                                )
                            }
                        },
                )
            }
        }
    }
}

@Preview(widthDp = 100, heightDp = 500, backgroundColor = 0xFF00FF)
@Composable
private fun LineNumbersPreview() {
    LineNumbers(
        modifier = Modifier.fillMaxSize(),
        layout = null,
        width = 100,
        textStyle = TextStyle(color = Color.Black),
        backgroundColor = Color.White,
        window = remember { mutableStateOf(IntRect(0, 0, 100, 500)) },
        zoom = { 1f },
        offsetY = { 0f },
    )
}
