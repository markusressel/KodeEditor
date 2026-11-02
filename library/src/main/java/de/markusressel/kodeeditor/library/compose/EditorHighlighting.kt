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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The styles of the text of a [KodeEditorState].
 *
 * The rules run on another thread, so they do not slow down typing. Until their result is there, the styles of the
 * text before move along with each change, so no color disappears. Only the first run waits for the result, so a
 * document does not appear without its colors.
 */
internal class EditorHighlighting(
    private val state: KodeEditorState,
    languageRuleBook: LanguageRuleBook,
    colorScheme: ColorScheme<SpanStyle>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val highlighter = AnnotatedStringHighlighter(languageRuleBook, colorScheme)

    private var highlightedVersion = state.rawVersion

    var styles by mutableStateOf(highlightNow(state.rawText))
        private set

    /** moves the styles with the text, in the same frame as the change */
    val spliceObserver: (TextSplice) -> Unit = { splice ->
        styles = styles.mapNotNull { it.movedBy(splice) }
    }

    private fun highlightNow(text: String) = runBlocking { highlighter.highlight(text) }.spanStyles

    /** runs the rules whenever the text changed, until it is cancelled */
    suspend fun keepUpdated() {
        snapshotFlow { state.version }.collectLatest { version ->
            if (version == highlightedVersion) return@collectLatest
            val text = state.rawText
            val result = withContext(dispatcher) { highlighter.highlight(text) }.spanStyles
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
