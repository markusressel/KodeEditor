package de.markusressel.kodeeditor.library.compose.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.fold.EditorFolds

/**
 * Unfolds what hides the current match of [search] while this is in the composition: folded lines open for it, and a
 * folded part of a line is open while it has the current match.
 *
 * @param search the search
 * @param folds the folds of the editor, null if it does not fold
 */
@Composable
internal fun SearchFoldsEffect(search: KodeEditorSearch, folds: EditorFolds?) {
    DisposableEffect(search, folds) {
        val reveal: (TextRange) -> Unit = { range -> folds?.reveal(range) }
        search.reveal = reveal
        onDispose {
            if (search.reveal === reveal) search.reveal = null
            folds?.inline?.shownMatch = null
        }
    }
    LaunchedEffect(search, folds) {
        val inline = folds?.inline ?: return@LaunchedEffect
        snapshotFlow { search.current }.collect { inline.shownMatch = it }
    }
}
