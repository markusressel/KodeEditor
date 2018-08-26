package de.markusressel.kodeeditor.library.syntaxhighlighter

import android.text.Editable
import android.text.Spannable
import android.text.style.CharacterStyle

/**
 * Interface for a SyntaxHighlighter with basic logic for color schemes and applying styles
 */
interface SyntaxHighlighter {

    /**
     * A set of styles that were applied by this highlighter
     */
    val appliedStyles: MutableSet<CharacterStyle>

    /**
     * The currently active color scheme
     */
    var colorScheme: SyntaxColorScheme

    /**
     * Get a set of rules for this highlighter
     */
    fun getRules(): Set<SyntaxHighlighterRule>

    /**
     * Get the default color scheme to use for this highlighter
     */
    fun getDefaultColorScheme(): SyntaxColorScheme

    /**
     * Highlight the given text
     *
     * Note: If you need to highlight multiple editables at the same time
     *       be sure to also create one highlighter instance for each editable.
     *       Otherwise applied styles might not be cleared properly
     *       when refreshing highlighting of an already highlighted editable.
     */
    fun highlight(editable: Editable) {
        // cleanup previously applied styles
        clearAppliedStyles(editable)

        // reapply
        getRules()
                .forEach { rule ->
                    rule
                            .findMatches(editable)
                            .forEach {
                                val start = it
                                        .range
                                        .start
                                val end = it.range.endInclusive + 1

                                // needs to be called for each result
                                // so multiple spans are created and applied
                                val styles = colorScheme
                                        .getStyles(rule)

                                highlight(editable, start, end, styles)
                            }
                }
    }

    /**
     * Apply a set of styles to a specific part of an editable
     *
     * @param editable the editable to highlight
     * @param start the starting position
     * @param end the end position (inclusive)
     * @param styles the styles to apply
     */
    private fun highlight(editable: Editable, start: Int, end: Int, styles: Set<() -> CharacterStyle>) {
        styles
                .forEach {
                    val style = it()
                    editable
                            .setSpan(style, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // remember which styles were applied
                    appliedStyles
                            .add(style)
                }
    }

    /**
     * Clear any modifications the syntax highlighter may have made to a given editable
     */
    fun clearAppliedStyles(editable: Editable) {
        appliedStyles
                .forEach {
                    editable
                            .removeSpan(it)
                }
        appliedStyles
                .clear()
    }

}