package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.layout.placedAt
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val CURSOR_BLINK_MS = 500L
private val CURSOR_WIDTH = 2.dp

/**
 * The blinking cursor of the local user, it stays on while the text or the selection changes.
 *
 * @param state the text, with the selection whose end the cursor is at
 * @param layout the layout of the text
 * @param color the color of the cursor
 * @param blinking whether the cursor blinks
 */
@Composable
internal fun EditorCursor(state: KodeEditorState, layout: EditorLineLayout, color: Color, blinking: Boolean) {
    var alpha by remember { mutableFloatStateOf(1f) }
    val width = LocalDensity.current.run { CURSOR_WIDTH.roundToPx() }.coerceAtLeast(1)

    if (blinking) {
        LaunchedEffect(state.version, state.selection) {
            alpha = 1f
            while (true) {
                delay(CURSOR_BLINK_MS)
                alpha = 1f - alpha
            }
        }
    }

    Box(
        modifier = Modifier
            .placedAt {
                val rect = layout.localCursorRect(state.selection.end)
                val left = rect.left.roundToInt()
                IntRect(left, rect.top.roundToInt(), left + width, rect.bottom.roundToInt())
            }
            .graphicsLayer { this.alpha = alpha }
            .background(color),
    )
}
