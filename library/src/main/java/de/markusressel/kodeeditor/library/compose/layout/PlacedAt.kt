package de.markusressel.kodeeditor.library.compose.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect

/**
 * Places the content at the rectangle given by [rect] and takes no space in the parent, so it works at any size of
 * the document. Reading the rectangle only affects the placement, nothing recomposes when it changes.
 */
internal fun Modifier.placedAt(rect: () -> IntRect) = layout { measurable, constraints ->
    val target = rect()
    val placeable = measurable.measure(Constraints.fixed(target.width, target.height))
    layout(constraints.minWidth, constraints.minHeight) {
        placeable.place(target.left, target.top)
    }
}
