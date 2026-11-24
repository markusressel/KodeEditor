package de.markusressel.kodeeditor.library.compose

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle

private const val FOLDED_LINE_SELECTION_ALPHA = 0.5f

/** how much of the selection color is in the box behind a folded part of a line */
private const val INLINE_FOLD_SELECTION_ALPHA = 0.7f
private const val FOLD_PLACEHOLDER_ALPHA = 0.6f
private const val SCROLLBAR_ALPHA = 0.4f
private val SEARCH_MATCH_COLOR = Color(0x55FFB300)
private val CURRENT_SEARCH_MATCH_COLOR = Color(0xAAFF8F00)

/** The colors the editor draws with, where the unspecified ones of [KodeEditorColors] got their defaults */
@Immutable
internal data class ResolvedEditorColors(
    val textStyle: TextStyle,
    val cursor: Color,
    /** the text of the bar, also of the fold arrows */
    val gutterText: Color,
    val foldedLineBackground: Color,
    val foldPlaceholder: Color,
    val inlineFoldBackground: Color,
    val inlineFoldText: Color,
    val searchMatch: Color,
    val currentSearchMatch: Color,
    val scrollbar: Color,
    val handle: Color,
)

/**
 * @param enabled whether the editor is enabled
 * @param textStyle the style of the text, its color is the text color of the colors if it has none
 * @return the colors to draw with, unspecified ones derived from the text, the bar and the selection
 */
@Composable
internal fun KodeEditorColors.resolve(enabled: Boolean, textStyle: TextStyle): ResolvedEditorColors {
    val fieldColors = textFieldColors(enabled = enabled).value
    val fieldTextColor = fieldColors.textColor(enabled).value
    val fieldTextStyle = textStyle.copy(color = textStyle.color.takeOrElse { fieldTextColor })
    val gutterText = lineNumberTextColor().value.takeOrElse { fieldTextStyle.color }
    val selectionColors = LocalTextSelectionColors.current
    val selection = selectionColors.backgroundColor
    return ResolvedEditorColors(
        textStyle = fieldTextStyle,
        cursor = fieldColors.cursorColor().value,
        gutterText = gutterText,
        foldedLineBackground = foldedLineBackgroundColor().value
            .takeOrElse { selection.copy(alpha = selection.alpha * FOLDED_LINE_SELECTION_ALPHA) },
        foldPlaceholder = foldPlaceholderColor().value
            .takeOrElse { gutterText.copy(alpha = gutterText.alpha * FOLD_PLACEHOLDER_ALPHA) },
        inlineFoldBackground = inlineFoldBackgroundColor().value
            .takeOrElse { selection.copy(alpha = selection.alpha * INLINE_FOLD_SELECTION_ALPHA) },
        inlineFoldText = inlineFoldTextColor().value.takeOrElse { gutterText },
        searchMatch = searchMatchBackgroundColor().value.takeOrElse { SEARCH_MATCH_COLOR },
        currentSearchMatch = currentSearchMatchBackgroundColor().value.takeOrElse { CURRENT_SEARCH_MATCH_COLOR },
        scrollbar = scrollbarColor().value.takeOrElse { gutterText.copy(alpha = gutterText.alpha * SCROLLBAR_ALPHA) },
        handle = selectionColors.handleColor,
    )
}
