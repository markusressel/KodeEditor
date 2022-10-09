package de.markusressel.kodeeditor.library.compose

import androidx.compose.material.MaterialTheme
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
     * Represents the color used for line numbers text.
     */
    @Composable
    fun lineNumberTextColor(): State<Color>

    /**
     * Represents the color used for the background of the line numbers.
     */
    @Composable
    fun lineNumberBackgroundColor(): State<Color>

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
        lineNumberTextColor: Color = Color.Unspecified,
        lineNumberBackgroundColor: Color = MaterialTheme.colors.onSurface,
    ): KodeEditorColors =
        DefaultKodeEditorColors(
            textFieldColors = textFieldColors,
            lineNumberTextColor = lineNumberTextColor,
            lineNumberBackgroundColor = lineNumberBackgroundColor,
        )
}


private data class DefaultKodeEditorColors(
    private val textFieldColors: KodeTextFieldColors,
    private val lineNumberTextColor: Color,
    private val lineNumberBackgroundColor: Color,
) : KodeEditorColors {

    @Composable
    override fun textFieldColors(enabled: Boolean): State<KodeTextFieldColors> {
        return rememberUpdatedState(textFieldColors)
    }

    @Composable
    override fun lineNumberTextColor(): State<Color> {
        return rememberUpdatedState(lineNumberTextColor)
    }

    @Composable
    override fun lineNumberBackgroundColor(): State<Color> {
        return rememberUpdatedState(lineNumberBackgroundColor)
    }

}
