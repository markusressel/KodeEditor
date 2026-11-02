package de.markusressel.kodeeditor.library.compose

import android.app.Application
import android.text.Selection
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Compares the connection with the one of the framework, which is what keyboards are written against.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class EditorInputConnectionTest {

    private val noActions = object : KeyboardActions {
        override fun onKeyEvent(event: KeyEvent) = false
        override fun onContextMenuAction(id: Int) = false
    }

    private fun framework(text: String): BaseInputConnection {
        val view = View(ApplicationProvider.getApplicationContext<Application>())
        return BaseInputConnection(view, true).also {
            val editable = it.editable!!
            editable.clear()
            editable.append(text)
            Selection.setSelection(editable, 0)
        }
    }

    private fun assertSame(step: String, framework: BaseInputConnection, state: KodeEditorState) {
        val editable = framework.editable!!
        assertEquals(step, editable.toString(), state.rawText)
        assertEquals(
            step,
            TextRange(Selection.getSelectionStart(editable), Selection.getSelectionEnd(editable)),
            state.selection,
        )
        val composingStart = BaseInputConnection.getComposingSpanStart(editable)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(editable)
        val composition = if (composingStart < 0) null else TextRange(composingStart, composingEnd)
        assertEquals(step, composition, state.composition)
    }

    @Test
    fun `any sequence of keyboard calls has the same result as the framework`() {
        val random = Random(4)
        repeat(300) { round ->
            val start = "some text\nwith lines"
            val framework = framework(start)
            val state = KodeEditorState(start)
            val connection = EditorInputConnection(state, noActions)
            val log = mutableListOf<String>()
            repeat(60) {
                val length = state.rawText.length
                val stateBefore = "'${state.rawText}' ${state.selection} ${state.composition}"
                val text = List(random.nextInt(5)) { "ab \n"[random.nextInt(4)] }.joinToString("")
                val a = random.nextInt(length + 3) - 1
                val b = random.nextInt(length + 3) - 1
                val cursor = random.nextInt(-3, 4)
                val before = random.nextInt(4)
                val after = random.nextInt(4)
                when (random.nextInt(7)) {
                    0 -> {
                        log += "commit '$text' $cursor"
                        connection.commitText(text, cursor)
                        framework.commitText(text, cursor)
                    }

                    1 -> {
                        log += "composing '$text' $cursor"
                        connection.setComposingText(text, cursor)
                        framework.setComposingText(text, cursor)
                    }

                    2 -> {
                        log += "finish"
                        connection.finishComposingText()
                        framework.finishComposingText()
                    }

                    3 -> {
                        log += "delete $before $after"
                        connection.deleteSurroundingText(before, after)
                        framework.deleteSurroundingText(before, after)
                    }

                    4 -> {
                        log += "select $a $b"
                        connection.setSelection(a, b)
                        framework.setSelection(a, b)
                    }

                    5 -> {
                        val first = a.coerceAtLeast(0)
                        val last = b.coerceAtLeast(0)
                        log += "region $first $last"
                        connection.setComposingRegion(first, last)
                        framework.setComposingRegion(first, last)
                    }

                    else -> {
                        val expected = framework.getTextBeforeCursor(5, 0).toString()
                        assertEquals(expected, connection.getTextBeforeCursor(5, 0).toString())
                        val expectedAfter = framework.getTextAfterCursor(5, 0).toString()
                        assertEquals(expectedAfter, connection.getTextAfterCursor(5, 0).toString())
                    }
                }
                assertSame("round $round: from $stateBefore ${log.takeLast(1)}", framework, state)
            }
        }
    }

    @Test
    fun `a large text is not sent to the keyboard as a whole`() {
        val state = KodeEditorState("x".repeat(500_000), TextRange(250_000))
        val connection = EditorInputConnection(state, noActions)
        val extracted = connection.getExtractedText(null, 0)
        assert(extracted.text.length <= 16 * 1024)
        assertEquals("x", extracted.text[extracted.selectionStart].toString())
        assert(connection.getTextBeforeCursor(1_000_000, 0).length <= 16 * 1024)
    }

    @Test
    fun `changes in a batch reach the keyboard once, at its end`() {
        val state = KodeEditorState("abc")
        var updates = 0
        state.inputLink = object : InputLink {
            override fun update() {
                updates++
            }

            override fun restart() = throw AssertionError("the keyboard made the change")
        }
        val connection = EditorInputConnection(state, noActions)
        connection.beginBatchEdit()
        connection.commitText("x", 1)
        connection.commitText("y", 1)
        assertEquals(0, updates)
        connection.endBatchEdit()
        assertEquals(1, updates)
    }

    @Test
    fun `surrogate pairs are deleted as a whole by code points`() {
        val state = KodeEditorState("a😀b", TextRange(3))
        EditorInputConnection(state, noActions).deleteSurroundingTextInCodePoints(1, 0)
        assertEquals("ab", state.rawText)
    }
}
