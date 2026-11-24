package de.markusressel.kodeeditor.library.compose.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAppearance
import kotlinx.coroutines.flow.collectLatest

/**
 * Lets the text of [state] appear with [appearance] while this is in the composition, frame by frame while text
 * appears. Several editors can show one state, the one that came last decides how text appears.
 *
 * @param state the text
 * @param appearance how text appears, null shows it at once
 */
@Composable
internal fun AppearingTextEffect(state: KodeEditorState, appearance: KodeEditorAppearance?) {
    val owner = remember { Any() }
    DisposableEffect(state, appearance) {
        state.showAppearance(owner, appearance)
        onDispose { state.showAppearance(owner, null) }
    }
    if (appearance == null) return
    LaunchedEffect(state, appearance) {
        // the system setting that scales animations, 0 when they are off. It can change at any time
        val motion = coroutineContext[MotionDurationScale]
        snapshotFlow { state.rangeAlphas.isNotEmpty() }.collectLatest { appearing ->
            // no frames while nothing appears
            while (appearing && withFrameMillis { state.onAppearFrame(it, motion?.scaleFactor ?: 1f) }) Unit
        }
    }
}
