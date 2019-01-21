package de.markusressel.kodeeditor.library.syntaxhighlighter

import android.text.Spannable
import android.text.style.CharacterStyle

/**
 * Interface for a SyntaxHighlighter with basic logic for color schemes and applying styles
 */
interface SyntaxHighlighter {

    /**
     * A set of styles that are currently applied by this highlighter
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
     * Note: If you need to highlight multiple spannables at the same time
     *       be sure to also create one highlighter instance for each spannable.
     *       Otherwise applied styles might not be cleared properly
     *       when refreshing highlighting of an already highlighted spannable.
     */
    fun highlight(spannable: Spannable) {
        // cleanup previously applied styles
        clearAppliedStyles(spannable)

        // reapply
        getRules()
                .forEach { rule ->
                    rule
                            .findMatches(spannable)
                            .forEach {
                                val start = it
                                        .range
                                        .start
                                val end = it.range.endInclusive + 1

                                // needs to be called for each result
                                // so multiple spans are created and applied
                                val styles = colorScheme
                                        .getStyles(rule)

                                highlight(spannable, start, end, styles)
                            }
                }
    }

    /**
     * Apply a set of styles to a specific part of an spannable
     *
     * @param spannable the spannable to highlight
     * @param start the starting position
     * @param end the end position (inclusive)
     * @param styles the styles to apply
     */
    private fun highlight(spannable: Spannable, start: Int, end: Int, styles: Set<() -> CharacterStyle>) {
        styles
                .forEach {
                    val style = it()
                    spannable.setSpan(style, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    // remember which styles were applied
                    appliedStyles.add(style)
                }
    }

    /**
     * Clear any modifications the syntax highlighter may have made to a given spannable
     */
    fun clearAppliedStyles(spannable: Spannable) {
        appliedStyles.forEach {
            spannable.removeSpan(it)
        }
        appliedStyles.clear()
    }

}