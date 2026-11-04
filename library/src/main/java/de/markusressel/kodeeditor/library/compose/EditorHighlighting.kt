package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.util.AnnotatedStringHighlighter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The styles of the text of a [KodeEditorState].
 *
 * The rules run on another thread, so they do not slow down typing. Until their result is there, the styles of the
 * text before move along with each change, so no color disappears. Only the first run waits for the result, so a
 * document does not appear without its colors. It does not wait for long, the time of the rules grows faster than the
 * text, and a large document would block the screen for seconds. Its colors arrive a moment later.
 *
 * @param firstRunLimitMs how long the first run can block the thread that creates this
 */
internal class EditorHighlighting(
    private val state: KodeEditorState,
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val firstRunLimitMs: Long = FIRST_RUN_LIMIT_MS,
) {
    private val highlighter = AnnotatedStringHighlighter(languageRuleBook, colorScheme)

    private var highlightedVersion = state.rawVersion

    /** the first run, if it took too long to wait for it */
    private var firstRun: Deferred<List<AnnotatedString.Range<SpanStyle>>>? = null

    var styles by mutableStateOf(firstStyles(state.rawText))
        private set

    /** moves the styles with the text, in the same frame as the change */
    val spliceObserver: (TextSplice) -> Unit = { splice ->
        styles = styles.mapNotNull { it.movedBy(splice) }
    }

    private fun firstStyles(text: String): List<AnnotatedString.Range<SpanStyle>> {
        val run = CoroutineScope(dispatcher).async { highlighter.highlight(text).spanStyles }
        val result = runBlocking { withTimeoutOrNull(firstRunLimitMs) { run.await() } }
        if (result == null) firstRun = run
        return result.orEmpty()
    }

    /** runs the rules whenever the text changed, until it is cancelled */
    suspend fun keepUpdated() {
        snapshotFlow { state.version }.collectLatest { version ->
            val pending = firstRun
            if (version == highlightedVersion && pending == null) return@collectLatest
            firstRun = null
            val text = state.rawText
            val result = if (pending != null && version == highlightedVersion) {
                try {
                    pending.await()
                } finally {
                    pending.cancel()
                }
            } else {
                pending?.cancel()
                withContext(dispatcher) { highlighter.highlight(text) }.spanStyles
            }
            // the text may have changed while the rules were running, and the next run is on its way then
            if (state.rawVersion == version) {
                highlightedVersion = version
                styles = result
            }
        }
    }

    private fun AnnotatedString.Range<SpanStyle>.movedBy(splice: TextSplice): AnnotatedString.Range<SpanStyle>? {
        // the common case: most styles are in front of the change, and stay the same object
        if (end <= splice.start) return this
        val newStart = mapPosition(start, splice)
        val newEnd = mapPosition(end, splice)
        if (newStart >= newEnd) return null
        return if (newStart == start && newEnd == end) this else AnnotatedString.Range(item, newStart, newEnd)
    }
}

/** a few frames, enough for the colors of a usual document to be there with its first frame */
private const val FIRST_RUN_LIMIT_MS = 100L
