package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Preview
@Composable
private fun LineNumbersPreview() {
    val text = (1..10).joinToString(separator = "\n", prefix = "Line: ")
    LineNumbers(
        text = text,
        backgroundColor = Color.White,
        textColor = Color.Black,
    )
}

@Composable
fun LineNumbers(
    modifier: Modifier = Modifier,
    text: String,
    textStyle: TextStyle = LocalTextStyle.current,
    backgroundColor: Color,
    textColor: Color = Color.Unspecified,
) {
    val lineNumbers by remember(text) {
        val lineCount = text.lines().size
        val lineText = (1..lineCount).joinToString(separator = "\n")
        mutableStateOf(lineText)
    }

    Box(modifier = modifier) {
        Text(
            modifier = Modifier
                .background(color = backgroundColor)
                .padding(start = 4.dp, end = 4.dp),
            text = lineNumbers,
            fontSize = textStyle.fontSize,
            color = textColor,
            textAlign = TextAlign.End,
        )
    }
}

