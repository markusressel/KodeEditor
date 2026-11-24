package de.markusressel.kodeeditor.demo

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import de.markusressel.kodeeditor.library.compose.reveal.KodeEditorReveal
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.view.KodeEditorViewState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** the intent extra that starts the demo in the showcase mode, see docs/showcase.md */
const val EXTRA_SHOWCASE = "showcase"

/** the intent extra that plays the scenario of the showcase mode, once the system bars are gone */
const val EXTRA_SHOWCASE_PLAY = "showcase_play"

/**
 * The scenario of the animation in the README, docs/showcase.md describes it and how to record it. It only uses public
 * API of the library, like an app would: Jane and the agent write at the same time, then sections fold, the view zooms
 * in and out, and the sections unfold again.
 *
 * @param state the text, with the sample text of the demo in it
 * @param viewState the view of the editor that shows [state]
 * @param agent reveals what the agent writes into [state]
 * @param janeTypes lets Jane type her line, returns when she is done
 */
internal suspend fun playShowcase(
    state: KodeEditorState,
    viewState: KodeEditorViewState,
    agent: KodeEditorReveal,
    janeTypes: suspend () -> Unit,
) = coroutineScope {
    // the first layout, and a moment to see the text as it is
    snapshotFlow { viewState.zoom }.first { it != null }
    // scripts/showcase.sh cuts the recording at these lines of the log
    Log.i(SHOWCASE_LOG_TAG, "start")
    delay(1000)

    val jane = launch { janeTypes() }
    delay(700)
    agentWrites(agent, state.text.indexOf("## Try it"), AGENT_TEXT, chunked = false)
    // until all of it is there
    snapshotFlow { agent.isRevealing }.first { !it }
    jane.join()
    delay(800)

    val foldedAway = state.lineOf("## Folded away")
    val agentSection = state.lineOf("## Written by an agent")
    viewState.setFolded(foldedAway, true)
    delay(900)
    viewState.setFolded(agentSection, true)
    delay(1200)

    viewState.zoomTo(1.8f)
    delay(1200)
    viewState.zoomTo(1f)
    delay(600)

    viewState.setFolded(agentSection, false)
    delay(700)
    viewState.setFolded(foldedAway, false)
    delay(1500)
    Log.i(SHOWCASE_LOG_TAG, "end")
}

private const val SHOWCASE_LOG_TAG = "KodeEditorShowcase"

/** @return the line that starts with [heading] */
private fun KodeEditorState.lineOf(heading: String): Int {
    val start = text.indexOf("\n$heading") + 1
    return text.substring(0, start).count { it == '\n' }
}
