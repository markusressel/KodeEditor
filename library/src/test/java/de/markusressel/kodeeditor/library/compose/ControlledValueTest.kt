package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledValueTest {

    private val state = KodeEditorState("abc", TextRange(3))
    private val reported = mutableListOf<TextFieldValue>()
    private val controlled = ControlledValue(state).also {
        it.onValueChange = { value -> reported += value }
        state.addListener(it)
    }

    /** the application hands back a value while the user typed further, which must not remove the newer input */
    @Test
    fun `a late value of the application does not take back newer input`() {
        state.replaceSelection("d")
        state.replaceSelection("e")
        state.replaceSelection("f")
        assertEquals("abcdef", state.text)

        reported.toList().forEach {
            controlled.onValueFromApplication(TextFieldValue(it.text, it.selection))
            assertEquals("abcdef", state.text)
        }
        assertEquals(TextRange(6), state.selection)
    }

    @Test
    fun `a value that was not reported replaces the text, without being reported back`() {
        state.replaceSelection("d")
        reported.clear()
        controlled.onValueFromApplication(TextFieldValue("something else", TextRange(4)))
        assertEquals("something else", state.text)
        assertEquals(TextRange(4), state.selection)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a value the application dropped the composition of keeps the composition`() {
        state.setSelectionFromKeyboard(TextRange(3), TextRange(0, 3))
        controlled.onValueFromApplication(TextFieldValue("abc", TextRange(3)))
        assertEquals(TextRange(0, 3), state.composition)
    }

    /** a reported value can come back after the user moved on, and going back to it later still has to work */
    @Test
    fun `an older reported value applied after its echo is a real change`() {
        state.replaceSelection("d")
        val old = reported.last()
        state.replaceSelection("e")
        controlled.onValueFromApplication(reported.last())
        controlled.onValueFromApplication(TextFieldValue(old.text, old.selection))
        assertEquals("abcd", state.text)
    }
}
