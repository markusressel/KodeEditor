package de.markusressel.kodeeditor.library.compose.input

/** What the keys and the keyboard do to folded text */
internal interface KeyFolds {
    /** @return true if a fold next to the cursor opened instead of being deleted into */
    fun openInsteadOfDelete(backward: Boolean): Boolean

    /** @return true if a fold at the cursor opened */
    fun expandAtCursor(): Boolean

    /** @return true if a fold at the cursor closed */
    fun collapseAtCursor(): Boolean
}
