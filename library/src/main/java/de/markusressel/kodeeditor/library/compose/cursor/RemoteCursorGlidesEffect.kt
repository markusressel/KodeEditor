package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.MotionDurationScale
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAppearance
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.SplicePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Remembers where the cursors of other users are drawn, and lets them glide frame by frame while they glide. The text
 * behind what others insert slides like their cursors glide.
 *
 * @param state the text
 * @param layout the layout of the editor
 * @param appearance how the changes of others move, null jumps
 * @param remoteCursors the cursors of the other users
 * @param visibleWidth how wide the visible text is
 * @return the glides, to draw the cursors
 */
@Composable
internal fun rememberRemoteCursorGlides(
    state: KodeEditorState,
    layout: EditorLineLayout,
    appearance: KodeEditorAppearance?,
    remoteCursors: List<KodeEditorRemoteCursor>,
    visibleWidth: () -> Float,
): RemoteCursorGlides {
    val glides = remember(layout) { RemoteCursorGlides(layout, visibleWidth) }
    DisposableEffect(state, glides) {
        state.addSpliceObserver(SplicePhase.Listeners, glides.spliceListener)
        onDispose { state.removeSpliceObserver(SplicePhase.Listeners, glides.spliceListener) }
    }
    SideEffect {
        glides.config = appearance?.cursorGlide
        // the text behind what others insert moves out of the way like their cursors move
        val slideMs = appearance?.let { it.cursorGlide?.durationMs ?: KodeEditorCursorGlide().durationMs }
        // the system setting is known a little later
        layout.insertSlideMs = { ((slideMs ?: 0) * glides.durationScale()).toInt() }
        layout.appearing.remoteCarets = glides::drawnCarets
        glides.update(remoteCursors)
    }
    LaunchedEffect(glides) {
        // the system setting that scales animations, 0 when they are off
        val motion = coroutineContext[MotionDurationScale]
        glides.durationScale = { motion?.scaleFactor ?: 1f }
        snapshotFlow { glides.isGliding to glides.starts }.collectLatest { (gliding) ->
            // frames only while a cursor glides or a label moves, they only draw and place again. A label that waits,
            // e.g. for its writer to rest, waits without frames
            while (gliding) {
                if (withFrameMillis { glides.onFrame(it) }) continue
                delay(glides.labels.waitMs() ?: break)
            }
        }
    }
    return glides
}
