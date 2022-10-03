package de.markusressel.kodeeditor.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import kotlinx.coroutines.runBlocking

@Composable
fun KodeEditor(
        modifier: Modifier = Modifier,
        initialText: String = "",
        languageRuleBook: LanguageRuleBook,
        colorScheme: ColorScheme = languageRuleBook.defaultColorScheme
) {
    val configuration = LocalConfiguration.current

    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText))
    }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }

    val visualTransformation = HighlightingTransformation(languageRuleBook, colorScheme)

    Scaffold(
            modifier = Modifier.background(Color.Black),
            backgroundColor = Color.Black
    ) { contentPadding ->
        BasicTextField(
                modifier = Modifier
                        .padding(contentPadding)
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
                value = text,
                onValueChange = { text = it },
                visualTransformation = visualTransformation,
        )
    }
}

class HighlightingTransformation(
        ruleBook: LanguageRuleBook,
        colorScheme: ColorScheme
) : VisualTransformation {

    private val highlighter = AnnotatedStringHighlighter(ruleBook, colorScheme)

    override fun filter(text: AnnotatedString): TransformedText {
        // TODO: run asynchronously
        val highlightedText = runBlocking {
            highlighter.highlight(text.text)
        }
        return TransformedText(highlightedText, OffsetMapping.Identity)
    }
}
