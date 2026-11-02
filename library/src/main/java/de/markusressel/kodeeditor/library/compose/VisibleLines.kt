package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect

/** enough single line layouts for the lines of a whole window at the lowest zoom, so unchanged lines are reused */
internal const val LINE_LAYOUT_CACHE_SIZE = 2048

/**
 * Places the content at the rectangle given by [rect] and takes no space in the parent, so it works at any size of
 * the document. Reading the rectangle only affects the placement, nothing recomposes when it changes.
 */
internal fun Modifier.placedAt(rect: () -> IntRect) = layout { measurable, constraints ->
    val target = rect()
    val placeable = measurable.measure(Constraints.fixed(target.width, target.height))
    layout(constraints.minWidth, constraints.minHeight) {
        placeable.place(target.left, target.top)
    }
}

/**
 * Draws the lines of [layout] that are within [window], and the selection below them.
 *
 * Android records every line of a text layout when it is drawn, whatever the clip is, which is far too slow for a
 * large document. So each line is drawn on its own. Single line layouts are cached by their content, so a line only
 * costs something the first time it is drawn.
 */
@Composable
internal fun VisibleLines(
    layout: TextLayoutResult,
    selection: TextRange?,
    textStyle: TextStyle,
    window: State<IntRect>,
    highlighting: CachedHighlighting,
) {
    val measurer = rememberTextMeasurer(cacheSize = LINE_LAYOUT_CACHE_SIZE)
    val selectionColor = LocalTextSelectionColors.current.backgroundColor

    Box(
        modifier = Modifier
            .placedAt { window.value }
            // its own layer, so nothing else that is drawn in the text field records these lines again
            .graphicsLayer {}
            .drawBehind {
                val area = window.value
                if (layout.lineCount == 0) return@drawBehind
                val lines = layout.getLineForVerticalPosition(area.top.toFloat())..
                    layout.getLineForVerticalPosition(area.bottom.toFloat())
                translate(-area.left.toFloat(), -area.top.toFloat()) {
                    if (selection != null && !selection.collapsed) {
                        val start = selection.min.coerceAtLeast(layout.getLineStart(lines.first))
                        val end = selection.max.coerceAtMost(layout.getLineEnd(lines.last))
                        if (start < end) drawPath(layout.getPathForRange(start, end), selectionColor)
                    }
                    val styles = highlighting.stylesOf(layout.layoutInput.text.text)
                    lineTexts(layout, lines, styles).forEachIndexed { index, line ->
                        if (line.isNotEmpty()) {
                            drawTextLine(layout, measurer, lines.first + index, line, textStyle)
                        }
                    }
                }
            },
    )
}

private fun DrawScope.drawTextLine(
    layout: TextLayoutResult,
    measurer: TextMeasurer,
    line: Int,
    text: AnnotatedString,
    textStyle: TextStyle,
) {
    val lineLayout = measurer.measure(text, style = textStyle, softWrap = false)
    drawText(
        textLayoutResult = lineLayout,
        topLeft = Offset(
            x = layout.getLineLeft(line) - lineLayout.getLineLeft(0),
            y = layout.getLineBaseline(line) - lineLayout.firstBaseline,
        ),
    )
}

/**
 * The text and the styles of each of the given [lines].
 *
 * [AnnotatedString.subSequence] looks at every style of the document, for each line. This looks at each one once.
 *
 * @param layout the text layout, its own styles (e.g. the underline of the text the keyboard is composing) are used too
 * @param lines the lines to get
 * @param styles further styles for the text of [layout]
 */
internal fun lineTexts(
    layout: TextLayoutResult,
    lines: IntRange,
    styles: List<AnnotatedString.Range<SpanStyle>> = emptyList(),
): List<AnnotatedString> {
    val text = layout.layoutInput.text
    val starts = IntArray(lines.count()) { layout.getLineStart(lines.first + it) }
    val ends = IntArray(lines.count()) { layout.getLineEnd(lines.first + it) }
    val spans = List(lines.count()) { mutableListOf<AnnotatedString.Range<SpanStyle>>() }
    val first = starts.first()
    val last = ends.last()

    (styles + text.spanStyles).forEach { span ->
        if (span.end <= first || span.start >= last) return@forEach
        val startLine = layout.getLineForOffset(span.start.coerceAtLeast(first)) - lines.first
        val endLine = layout.getLineForOffset((span.end - 1).coerceAtMost(last - 1)) - lines.first
        for (index in startLine.coerceAtLeast(0)..endLine.coerceAtMost(lines.count() - 1)) {
            val start = span.start.coerceAtLeast(starts[index]) - starts[index]
            val end = span.end.coerceAtMost(ends[index]) - starts[index]
            if (start < end) spans[index] += AnnotatedString.Range(span.item, start, end)
        }
    }

    return List(lines.count()) { index ->
        val line = text.text.substring(starts[index], ends[index])
        val visible = line.removeSuffix("\n")
        AnnotatedString(
            text = visible,
            spanStyles = spans[index].mapNotNull { span ->
                val end = span.end.coerceAtMost(visible.length)
                if (span.start < end) span.copy(end = end) else null
            },
        )
    }
}
