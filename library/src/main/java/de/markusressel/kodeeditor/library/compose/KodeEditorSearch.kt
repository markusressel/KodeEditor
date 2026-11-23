package de.markusressel.kodeeditor.library.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * What a [KodeEditorSearch] looks for.
 *
 * @param text the text to find, or a regular expression with [regex]. Empty finds nothing
 * @param ignoreCase whether upper and lower case letters match each other
 * @param wholeWord whether a match must not have a word character right before or after it. Word characters are
 * letters, digits and `_`, like for selecting a word
 * @param regex whether [text] is a regular expression, see [Regex]
 */
@Immutable
data class KodeEditorSearchQuery(
    val text: String,
    val ignoreCase: Boolean = true,
    val wholeWord: Boolean = false,
    val regex: Boolean = false,
)

/**
 * Finds a [query] in the text of a [KodeEditorState], and replaces it. Pass it to a [KodeEditor] to highlight all
 * [matches] and the [current] one. There is no search bar, the application shows its own.
 *
 * The text is searched on another thread whenever it or the query changes, while [keepUpdated] runs, which
 * [rememberKodeEditorSearch] does. Until a search is done, the matches move with the changes of the text, like range
 * styles do. Text that a range style hides with a fully transparent color, like text an agent did not type yet, is not
 * searched until it is shown.
 *
 * [next] and [previous] do not move the cursor and do not scroll, so they also work in a read-only editor. Scroll with
 * [KodeEditorViewState.scrollToRange] and select with [KodeEditorState.setSelection] if that is wanted. A match in a
 * folded part unfolds it when it becomes the current match.
 *
 * @param state the text to search
 */
@Stable
class KodeEditorSearch(private val state: KodeEditorState) {

    /** what to find, null finds nothing and shows nothing */
    var query: KodeEditorSearchQuery? by mutableStateOf(null)

    /** the matches of the whole text, also those in hidden text, moved with each change of the text */
    private var found by mutableStateOf(emptyList<TextRange>())

    /** where the current match is, moved with each change of the text */
    private var currentAnchor by mutableStateOf<TextRange?>(null)

    private val visibleMatches = derivedStateOf {
        val hidden = state.rangeStyles.filter { it.item.color.isSpecified && it.item.color.alpha == 0f }
        if (hidden.isEmpty()) found else found.filter { match -> hidden.none { it.start < match.max && it.end > match.min } }
    }

    /**
     * The matches in the order of the text, without those in hidden text. Reading this in a composition or a draw
     * updates it when it changes.
     */
    val matches: List<TextRange> get() = visibleMatches.value

    private val currentMatch = derivedStateOf {
        val anchor = currentAnchor ?: return@derivedStateOf null
        val all = matches
        if (all.isEmpty()) return@derivedStateOf null
        // the match itself, or when it is gone the next one after its place
        all.firstOrNull { it.min >= anchor.min } ?: all.first()
    }

    /**
     * The match that [next] or [previous] went to, null before that, after the query changed and while there are no
     * matches. When the text of the current match changes so it does not match anymore, the next match after its place
     * becomes the current one.
     */
    val current: TextRange? get() = currentMatch.value

    /** the place of [current] in [matches], from 0, null without a current match */
    val currentIndex: Int?
        get() {
            val match = current ?: return null
            return matches.binarySearchBy(match.min) { it.min }.takeIf { it >= 0 }
        }

    /** why the query finds nothing, e.g. an invalid regular expression or one that takes too long, null otherwise */
    var error: String? by mutableStateOf(null)
        private set

    /** whether the text is being searched, [matches] are the ones of the last search until it is done */
    var isSearching: Boolean by mutableStateOf(false)
        private set

    /** whether the search stopped at [MAX_MATCHES] matches, so there are more in the text */
    var isLimited: Boolean by mutableStateOf(false)
        private set

    /** unfolds what hides a range, set by the editor that shows this search */
    internal var reveal: ((TextRange) -> Unit)? = null

    private var searchedVersion = -1
    private var searchedQuery: KodeEditorSearchQuery? = null

    /** moves the matches with each change of the text */
    private val spliceListener: (TextSplice) -> Unit = { splice ->
        found = found.mapNotNull { it.mappedInside(splice) }
        currentAnchor = currentAnchor?.let { it.mappedInside(splice) ?: TextRange(mapPosition(it.min, splice)) }
    }

    /**
     * Goes to the next match, from the end of the current match, or from the cursor without one. After the last match
     * it starts again at the first one.
     *
     * @param from where to look from, the first match that starts there or behind it is the next one
     * @return the new current match, null if there are no matches
     */
    fun next(from: Int = current?.max ?: state.selection.max): TextRange? {
        val all = matches
        val match = all.firstOrNull { it.min >= from && it != current } ?: all.firstOrNull() ?: return null
        return goTo(match)
    }

    /**
     * Goes to the previous match, from the start of the current match, or from the cursor without one. Before the
     * first match it starts again at the last one.
     *
     * @param from where to look from, the last match that ends there or before it is the previous one
     * @return the new current match, null if there are no matches
     */
    fun previous(from: Int = current?.min ?: state.selection.min): TextRange? {
        val all = matches
        val match = all.lastOrNull { it.max <= from && it != current } ?: all.lastOrNull() ?: return null
        return goTo(match)
    }

    private fun goTo(match: TextRange): TextRange {
        currentAnchor = match
        reveal?.invoke(match)
        return match
    }

    /**
     * Replaces the current match, as a change of the user that can be undone, also in a read-only editor. The cursor
     * goes behind the replacement, and the next match becomes the current one.
     *
     * @param replacement the new text. With [KodeEditorSearchQuery.regex], `$1` or `${name}` insert groups of the match
     * and `\$` a dollar sign, see [Regex.replace]
     * @return false if there is no current match
     */
    fun replaceCurrent(replacement: String): Boolean {
        val match = current ?: return false
        val query = query ?: return false
        val text = state.text
        val inserted = replacementFor(query, text, match, replacement) ?: return false
        state.localEdit(match.min, match.max, inserted)
        // the replacement is no match until the text is searched again
        val replaced = TextRange(match.min, match.min + inserted.length)
        found = found.filterNot { it == replaced }
        currentAnchor = TextRange(replaced.max)
        return true
    }

    /**
     * Replaces all [matches] in one change that can be undone at once, also in a read-only editor.
     *
     * @param replacement the new text, like for [replaceCurrent]
     * @return how many matches were replaced
     */
    fun replaceAll(replacement: String): Int {
        val query = query ?: return 0
        val all = matches
        if (all.isEmpty()) return 0
        val text = state.text
        val result = StringBuilder(text.length)
        var copied = 0
        var replaced = 0
        for (match in all) {
            val inserted = replacementFor(query, text, match, replacement) ?: continue
            result.append(text, copied, match.min).append(inserted)
            copied = match.max
            replaced++
        }
        if (replaced == 0) return 0
        result.append(text, copied, text.length)
        val first = all.first().min
        val lastEnd = all.last().max
        val changedEnd = result.length - (text.length - lastEnd)
        // one change from the first to the end of the last match, so it is one step to undo
        state.localEdit(first, lastEnd, result.substring(first, changedEnd), TextRange(changedEnd))
        currentAnchor = null
        return replaced
    }

    /**
     * Searches the text whenever it or the [query] changes, until it is cancelled. [rememberKodeEditorSearch] runs this.
     *
     * @param dispatcher where the text is searched
     */
    suspend fun keepUpdated(dispatcher: CoroutineDispatcher = Dispatchers.Default) {
        state.addSpliceListener(spliceListener)
        try {
            search(dispatcher)
        } finally {
            state.removeSpliceListener(spliceListener)
        }
    }

    private suspend fun search(dispatcher: CoroutineDispatcher) {
        snapshotFlow { query to state.version }.collectLatest { (query, version) ->
            if (query != searchedQuery) currentAnchor = null
            if (query == searchedQuery && version == searchedVersion) return@collectLatest
            if (query == null || query.text.isEmpty()) {
                onSearched(query, version, emptyList(), null, false)
                return@collectLatest
            }
            val text = state.rawText
            isSearching = true
            try {
                val result = withContext(dispatcher) { find(text, query) }
                // the text may have changed meanwhile, then the next search is on its way
                if (state.rawVersion == version) onSearched(query, version, result.matches, result.error, result.limited)
            } finally {
                isSearching = false
            }
        }
    }

    private fun onSearched(
        query: KodeEditorSearchQuery?,
        version: Int,
        matches: List<TextRange>,
        error: String?,
        limited: Boolean,
    ) {
        found = matches
        this.error = error
        isLimited = limited
        searchedQuery = query
        searchedVersion = version
    }

    private class Result(val matches: List<TextRange>, val error: String?, val limited: Boolean)

    private suspend fun find(text: String, query: KodeEditorSearchQuery): Result {
        val regex = try {
            query.toRegex()
        } catch (e: IllegalArgumentException) {
            return Result(emptyList(), e.message ?: "invalid regular expression", false)
        }
        val job = currentCoroutineContext()[Job]
        val deadline = System.nanoTime() + REGEX_TIMEOUT_NS
        val checked = CheckedCharSequence(text) {
            job?.ensureActive()
            if (System.nanoTime() > deadline) throw SearchTimeout()
        }
        val matches = ArrayList<TextRange>()
        try {
            var position = 0
            while (position <= text.length) {
                currentCoroutineContext().ensureActive()
                val match = regex.find(checked, position) ?: break
                val range = match.range
                if (!range.isEmpty()) {
                    if (matches.size == MAX_MATCHES) return Result(matches, null, true)
                    matches += TextRange(range.first, range.last + 1)
                }
                // an empty match would find itself again
                position = if (range.isEmpty()) range.first + 1 else range.last + 1
            }
        } catch (e: SearchTimeout) {
            return Result(emptyList(), "the search took too long", false)
        }
        return Result(matches, null, false)
    }

    private class SearchTimeout : CancellationException("the search took too long")

    companion object {
        /** the most matches a search finds, see [isLimited] */
        const val MAX_MATCHES = 10_000

        private const val REGEX_TIMEOUT_NS = 2_000_000_000L
    }
}

/** the regular expression of the query, it throws for an invalid one */
internal fun KodeEditorSearchQuery.toRegex(): Regex {
    val pattern = if (regex) text else Regex.escape(text)
    val bounded = if (wholeWord) "(?<![\\p{L}\\p{N}_])(?:$pattern)(?![\\p{L}\\p{N}_])" else pattern
    val options = buildSet {
        add(RegexOption.MULTILINE)
        if (ignoreCase) {
            add(RegexOption.IGNORE_CASE)
        }
    }
    // unicode case folding, not only of ASCII letters
    val flags = if (ignoreCase) "(?u)" else ""
    return Regex(flags + bounded, options)
}

/** @return what replaces [match], null if it does not match there anymore */
private fun replacementFor(query: KodeEditorSearchQuery, text: String, match: TextRange, replacement: String): String? {
    if (!query.regex) return replacement
    val matcher = query.toRegex().toPattern().matcher(text)
    // the match is looked for again where it starts, lookbehinds and anchors see the text around it
    matcher.region(match.min, text.length)
    matcher.useTransparentBounds(true)
    matcher.useAnchoringBounds(false)
    if (!matcher.lookingAt() || matcher.end() != match.max) return null
    return replaceGroups(matcher, replacement)
}

/** [replacement] with `$1`, `${name}` and `\` escapes like [Regex.replace] */
private fun replaceGroups(matcher: java.util.regex.Matcher, replacement: String): String {
    val out = StringBuilder()
    var index = 0
    while (index < replacement.length) {
        val char = replacement[index]
        when {
            char == '\\' && index + 1 < replacement.length -> {
                out.append(replacement[index + 1])
                index += 2
            }

            char == '$' && index + 1 < replacement.length && replacement[index + 1] == '{' -> {
                val end = replacement.indexOf('}', index + 2)
                require(end > 0) { "missing } in the replacement" }
                out.append(matcher.group(replacement.substring(index + 2, end)) ?: "")
                index = end + 1
            }

            char == '$' && index + 1 < replacement.length && replacement[index + 1].isDigit() -> {
                var end = index + 1
                var group = replacement[end] - '0'
                end++
                // the longest group number that exists
                while (end < replacement.length && replacement[end].isDigit()) {
                    val longer = group * 10 + (replacement[end] - '0')
                    if (longer > matcher.groupCount()) break
                    group = longer
                    end++
                }
                out.append(matcher.group(group) ?: "")
                index = end
            }

            else -> {
                out.append(char)
                index++
            }
        }
    }
    return out.toString()
}

/** a text that calls [check] now and then while a regular expression reads it, so a slow one can be stopped */
private class CheckedCharSequence(private val text: CharSequence, private val check: () -> Unit) : CharSequence {
    private var reads = 0
    override val length: Int get() = text.length

    override fun get(index: Int): Char {
        if (++reads and CHECK_EVERY == 0) check()
        return text[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        CheckedCharSequence(text.subSequence(startIndex, endIndex), check)

    override fun toString() = text.toString()

    private companion object {
        const val CHECK_EVERY = 0xFFF
    }
}

/**
 * @param state the text to search
 * @return a [KodeEditorSearch] that searches [state] while it is in the composition, to pass to a [KodeEditor]
 */
@Composable
fun rememberKodeEditorSearch(state: KodeEditorState): KodeEditorSearch {
    val search = remember(state) { KodeEditorSearch(state) }
    LaunchedEffect(search) { search.keepUpdated() }
    return search
}
