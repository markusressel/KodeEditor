package de.markusressel.kodeeditor.library.compose.cursor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.KodeEditor

/**
 * The cursors and selections of another user, which a [KodeEditor] draws on top of its text.
 *
 * @param id tells the users apart, it keys the movement of the cursors and the label
 * @param selections the selections of the user, in UTF-16 characters of the text. The end of each one is where its
 * cursor is, a collapsed one is only a cursor. The label is at the cursor of the first one
 * @param caretColor the color of the cursors
 * @param selectionColor the color behind the selected text, which should not be opaque
 * @param labelVisibility whether the label is shown by the rules of the editor, always or never
 * @param labelSignal change it, e.g. count it up, to show the label fully for a moment, like when this user is picked
 * in a list of users or asks for attention. Its first value shows nothing
 */
@Immutable
data class KodeEditorRemoteCursor(
    val id: String,
    val selections: List<TextRange>,
    val caretColor: Color,
    val selectionColor: Color,
    val labelVisibility: KodeEditorLabelVisibility = KodeEditorLabelVisibility.Auto,
    val labelSignal: Int = 0,
)

/** whether the label of another user is shown, see [KodeEditorRemoteCursor.labelVisibility] */
enum class KodeEditorLabelVisibility {
    /**
     * by the rules of the editor: it fades down while the user writes steadily, and while it covers the cursor of this
     * editor, see docs/reveal.md
     */
    Auto,

    /** always fully */
    Shown,

    /** never, the cursor still shows the user */
    Hidden,
}

/**
 * How the cursors of other users move to their new place, see [KodeEditorRemoteCursor]. A cursor glides when its user
 * moved it a little, also while it follows text that is revealed piece after piece: a new place during a glide glides
 * on from where the cursor is drawn. It jumps for all other moves, when the text in front of it changed, when only the
 * layout moves it, e.g. for a fold, a zoom or a new size, and for moves of less than half a pixel. The cursor of this
 * editor never glides. A selection glides with its cursor: its other end grows or shrinks with it, however far. A user
 * who comes fades in, and one who leaves fades out, with the cursors, selections and label. A user who comes back
 * while fading out fades in again from there. The system setting
 * that scales the duration of animations scales this too, and turns it off with a scale of 0. The defaults are those of
 * the MkDocsEditor web client.
 *
 * @param durationMs how long a cursor takes to its new place, it slows down towards the end (cubic ease-out). A cursor
 * that gets a new place while it still glides, or within 90 ms of its last one, takes 1.5 times the time since that
 * one, at most this, so it keeps close behind a fast series of places, like the head of revealed text
 * @param sameRowWidthFraction how far a cursor may move within its row and still glide, as a part of the width of
 * the text area
 * @param nextRowLines how far sideways a cursor may move to the row above or below and still glide, in line heights
 * @param fadeOutMs how long a user who leaves takes to fade out, 0 removes it at once
 * @param fadeInMs how long a user who comes takes to fade in, 0 shows it at once
 */
@Immutable
data class KodeEditorCursorGlide(
    val durationMs: Int = 120,
    val sameRowWidthFraction: Float = 0.4f,
    val nextRowLines: Float = 3f,
    val fadeOutMs: Int = 200,
    val fadeInMs: Int = 200,
)
