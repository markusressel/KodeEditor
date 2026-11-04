package de.markusressel.kodeeditor.library.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import de.markusressel.kodehighlighter.core.ui.KodeTextField
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldColors
import de.markusressel.kodehighlighter.core.ui.KodeTextFieldDefaults


/**
 * Represents the colors of the code editor (without highlighting), line numbers, etc. used
 * in a code editor in different states.
 *
 * See [KodeEditorDefaults.editorColors] for the default colors used in [KodeEditorDefaults].
 */
@Stable
interface KodeEditorColors {

    /**
     * [KodeTextField] specific set of colors
     *
     * @param enabled whether the text field is enabled
     */
    @Composable
    fun textFieldColors(enabled: Boolean): State<KodeTextFieldColors>

    /**
     * Represents the color used for the background of the editor text.
     */
    @Composable
    fun textFieldBackgroundColor(): State<Color>

    /**
     * Represents the color used for line numbers text.
     */
    @Composable
    fun lineNumberTextColor(): State<Color>

    /**
     * Represents the color used for the background of the line numbers.
     */
    @Composable
    fun lineNumberBackgroundColor(): State<Color>

    /**
     * Represents the color behind a folded line, which should not be opaque. Unspecified uses a part of the selection
     * color.
     */
    @Composable
    fun foldedLineBackgroundColor(): State<Color> = rememberUpdatedState(Color.Unspecified)

    /**
     * Represents the color of the placeholder behind a folded line. Unspecified uses a part of the line number color.
     */
    @Composable
    fun foldPlaceholderColor(): State<Color> = rememberUpdatedState(Color.Unspecified)

    /**
     * Represents the color of the box behind a folded part of a line. Unspecified uses a part of the selection color.
     */
    @Composable
    fun inlineFoldBackgroundColor(): State<Color> = rememberUpdatedState(Color.Unspecified)

    /**
     * Represents the color of the text in the box behind a folded part of a line. Unspecified uses the line number
     * color.
     */
    @Composable
    fun inlineFoldTextColor(): State<Color> = rememberUpdatedState(Color.Unspecified)

}


/**
 * Contains the default values used by [KodeTextField].
 */
@Immutable
object KodeEditorDefaults {

    /**
     * Creates a [KodeEditorColors] that represents the default input text, background and content
     * (including label, placeholder, leading and trailing icons) colors used in a [KodeEditor].
     */
    @Composable
    fun editorColors(
        textFieldColors: KodeTextFieldColors = KodeTextFieldDefaults.textFieldColors(),
        textFieldBackgroundColor: Color = MaterialTheme.colorScheme.surface,
        lineNumberTextColor: Color = MaterialTheme.colorScheme.onSurface,
        lineNumberBackgroundColor: Color = MaterialTheme.colorScheme.surface,
        foldedLineBackgroundColor: Color = Color.Unspecified,
        foldPlaceholderColor: Color = Color.Unspecified,
        inlineFoldBackgroundColor: Color = Color.Unspecified,
        inlineFoldTextColor: Color = Color.Unspecified,
    ): KodeEditorColors =
        DefaultKodeEditorColors(
            textFieldColors = textFieldColors,
            textFieldBackgroundColor = textFieldBackgroundColor,
            lineNumberTextColor = lineNumberTextColor,
            lineNumberBackgroundColor = lineNumberBackgroundColor,
            foldedLineBackgroundColor = foldedLineBackgroundColor,
            foldPlaceholderColor = foldPlaceholderColor,
            inlineFoldBackgroundColor = inlineFoldBackgroundColor,
            inlineFoldTextColor = inlineFoldTextColor,
        )
}


private data class DefaultKodeEditorColors(
    private val textFieldColors: KodeTextFieldColors,
    private val textFieldBackgroundColor: Color,
    private val lineNumberTextColor: Color,
    private val lineNumberBackgroundColor: Color,
    private val foldedLineBackgroundColor: Color,
    private val foldPlaceholderColor: Color,
    private val inlineFoldBackgroundColor: Color,
    private val inlineFoldTextColor: Color,
) : KodeEditorColors {

    @Composable
    override fun textFieldColors(enabled: Boolean): State<KodeTextFieldColors> {
        return rememberUpdatedState(textFieldColors)
    }

    @Composable
    override fun textFieldBackgroundColor(): State<Color> {
        return rememberUpdatedState(textFieldBackgroundColor)
    }

    @Composable
    override fun lineNumberTextColor(): State<Color> {
        return rememberUpdatedState(lineNumberTextColor)
    }

    @Composable
    override fun lineNumberBackgroundColor(): State<Color> {
        return rememberUpdatedState(lineNumberBackgroundColor)
    }

    @Composable
    override fun foldedLineBackgroundColor(): State<Color> {
        return rememberUpdatedState(foldedLineBackgroundColor)
    }

    @Composable
    override fun foldPlaceholderColor(): State<Color> {
        return rememberUpdatedState(foldPlaceholderColor)
    }

    @Composable
    override fun inlineFoldBackgroundColor(): State<Color> {
        return rememberUpdatedState(inlineFoldBackgroundColor)
    }

    @Composable
    override fun inlineFoldTextColor(): State<Color> {
        return rememberUpdatedState(inlineFoldTextColor)
    }

}
