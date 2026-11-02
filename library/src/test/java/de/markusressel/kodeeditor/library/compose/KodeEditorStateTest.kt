package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KodeEditorStateTest {

    private fun checkIndex(text: String, index: LineIndex) {
        val expected = mutableListOf(0)
        text.forEachIndexed { position, char -> if (char == '\n') expected += position + 1 }
        assertEquals(expected, List(index.count) { index.start(it) })
    }

    @Test
    fun `the line index stays correct for any change`() {
        val random = Random(1)
        var text = "one\ntwo\n\nthree"
        val index = LineIndex(text)
        repeat(5000) {
            val start = random.nextInt(text.length + 1)
            val removed = random.nextInt((text.length - start).coerceAtMost(12) + 1)
            val inserted = List(random.nextInt(8)) { "ab\n"[random.nextInt(3)] }.joinToString("")
            index.apply(text, TextSplice(start, removed, inserted))
            text = text.replaceRange(start, start + removed, inserted)
            checkIndex(text, index)
        }
    }

    @Test
    fun `lines can be looked up by offset`() {
        val state = KodeEditorState("one\ntwo\n\nthree")
        assertEquals(4, state.lineCount)
        assertEquals(0, state.lineOf(3))
        assertEquals(1, state.lineOf(4))
        assertEquals(2, state.lineOf(8))
        assertEquals(3, state.lineOf(14))
        assertEquals(4, state.lineStart(1))
        assertEquals(7, state.lineEnd(1))
        assertEquals(14, state.lineEnd(3))
        assertEquals(3, state.longestLine())
    }

    @Test
    fun `a local edit is reported at once, in order, as a splice`() {
        val state = KodeEditorState("hello")
        val splices = mutableListOf<TextSplice>()
        state.addListener(object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) {
                splices += splice
                assertEquals("hello world", state.rawText.substring(0, splice.start + splice.inserted.length))
            }
        })
        state.setSelection(TextRange(5))
        state.replaceSelection(" world")
        assertEquals(listOf(TextSplice(5, 0, " world")), splices)
    }

    @Test
    fun `remote changes are not reported, and move the selection with the text`() {
        val state = KodeEditorState("0123456789", TextRange(4, 6))
        state.addListener(object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) = throw AssertionError("reported $splice")
        })
        state.applyRemote(listOf(TextSplice(0, 0, "ab")))
        assertEquals("ab0123456789", state.text)
        assertEquals(TextRange(6, 8), state.selection)
        state.applyRemote(listOf(TextSplice(7, 2, "")))
        assertEquals("ab01234789", state.text)
        assertEquals(TextRange(6, 7), state.selection)
    }

    @Test
    fun `remote text at the cursor stays behind the cursor`() {
        val state = KodeEditorState("abc", TextRange(1))
        state.applyRemote(listOf(TextSplice(1, 0, "X")))
        assertEquals("aXbc", state.text)
        assertEquals(TextRange(1), state.selection)
    }

    /** what the keyboard sends next replaces the composition, which must not hold the text of someone else */
    @Test
    fun `remote text at the edges of the composition stays out of it`() {
        val state = KodeEditorState("say word", TextRange(8))
        state.setSelectionFromKeyboard(TextRange(8), TextRange(4, 8))
        state.applyRemote(listOf(TextSplice(4, 0, "A")))
        assertEquals(TextRange(5, 9), state.composition)
        state.applyRemote(listOf(TextSplice(9, 0, "B")))
        assertEquals(TextRange(5, 9), state.composition)
        state.applyRemote(listOf(TextSplice(6, 1, "")))
        assertEquals(TextRange(5, 8), state.composition)
        state.applyRemote(listOf(TextSplice(5, 3, "")))
        assertNull(state.composition)
    }

    /** a restart ends a key repeat, and what the keyboard sends during it gets lost */
    @Test
    fun `the keyboard is only started over when the whole text was replaced`() {
        val state = KodeEditorState("x".repeat(100), TextRange(50))
        val calls = mutableListOf<String>()
        state.inputLink = object : InputLink {
            override fun update() {
                calls += "update"
            }

            override fun restart() {
                calls += "restart"
            }
        }
        state.applyRemote(listOf(TextSplice(50, 0, "y")))
        state.replaceSelection("z")
        state.localEdit(0, 1, "")
        state.undo()
        state.redo()
        assertEquals(List(calls.size) { "update" }, calls)
        assertTrue(calls.isNotEmpty())

        state.setText("something else")
        assertEquals("restart", calls.last())
        state.edit("something new", TextRange(3))
        assertEquals("restart", calls.last())
    }

    @Test
    fun `an edit of the application is reported, can be undone and only changes what differs`() {
        val state = KodeEditorState("hello world", TextRange(5))
        val splices = mutableListOf<TextSplice>()
        state.addListener(object : KodeEditorListener {
            override fun onLocalEdit(splice: TextSplice) {
                splices += splice
            }
        })
        state.edit("hello big world", TextRange(9))
        assertEquals(listOf(TextSplice(6, 0, "big ")), splices)
        assertEquals(TextRange(9), state.selection)
        state.undo()
        assertEquals("hello world", state.text)
    }

    @Test
    fun `setText only changes what differs and keeps the cursor with the text`() {
        val state = KodeEditorState("hello world", TextRange(8))
        state.setText("hello big world")
        assertEquals(TextRange(12), state.selection)
    }

    @Test
    fun `typing is one undo step, and undo and redo restore text and selection`() {
        var time = 0L
        val stack = UndoStack { time }
        stack.record(TextSplice(0, 0, "a"), "", TextRange(0), TextRange(1))
        time += 10
        stack.record(TextSplice(1, 0, "b"), "", TextRange(1), TextRange(2))
        time += 5000
        stack.record(TextSplice(2, 0, "c"), "", TextRange(2), TextRange(3))
        val last = stack.undo()!!
        assertEquals(TextSplice(2, 1, ""), last.splice)
        val first = stack.undo()!!
        assertEquals(TextSplice(0, 2, ""), first.splice)
        assertEquals(TextRange(0), first.selection)
        assertFalse(stack.canUndo)
        assertTrue(stack.canRedo)
        assertEquals(TextSplice(0, 0, "ab"), stack.redo()!!.splice)
    }

    @Test
    fun `undo steps a remote change overlaps are dropped, others move with the text`() {
        val state = KodeEditorState("0123456789", TextRange(5))
        state.replaceSelection("X")
        state.applyRemote(listOf(TextSplice(0, 0, "aa")))
        state.undo()
        assertEquals("aa0123456789", state.text)

        state.setSelection(TextRange(5))
        state.replaceSelection("Y")
        state.applyRemote(listOf(TextSplice(5, 2, "")))
        assertFalse(state.canUndo)
    }

    /** undoing everything gives back the text it started with, also with remote changes far from the edits */
    @Test
    fun `undo always gets back to the start`() {
        val random = Random(2)
        repeat(200) {
            val start = "start of the text\nwith two lines"
            val state = KodeEditorState(start)
            repeat(random.nextInt(30)) {
                val a = random.nextInt(state.text.length + 1)
                val b = (a + random.nextInt(5)).coerceAtMost(state.text.length)
                state.localEdit(a, b, List(random.nextInt(4)) { "xy\n"[random.nextInt(3)] }.joinToString(""))
            }
            while (state.canUndo) state.undo()
            assertEquals(start, state.text)
        }
    }

    /**
     * What an application does with the reported changes: applying them to its own copy, and sending changes of other
     * users. In any order, the copy and the editor stay the same.
     */
    @Test
    fun `an application that replays the reported changes never differs from the editor`() {
        val random = Random(3)
        repeat(300) { round ->
            var copy = "start of the text\nwith two lines\nand a third one"
            val state = KodeEditorState(copy)
            state.addListener(object : KodeEditorListener {
                override fun onLocalEdit(splice: TextSplice) {
                    copy = copy.replaceRange(splice.start, splice.start + splice.removedLength, splice.inserted)
                }
            })
            val connection = EditorInputConnection(state, object : KeyboardActions {
                override fun onKeyEvent(event: android.view.KeyEvent) = false
                override fun onContextMenuAction(id: Int) = false
            })
            repeat(100) {
                val length = state.rawText.length
                when (random.nextInt(9)) {
                    0 -> connection.commitText(randomText(random), random.nextInt(-2, 3))
                    1 -> connection.setComposingText(randomText(random), 1)
                    2 -> connection.finishComposingText()
                    3 -> connection.deleteSurroundingText(random.nextInt(4), random.nextInt(4))
                    4 -> connection.setSelection(random.nextInt(length + 1), random.nextInt(length + 1))
                    5 -> connection.setComposingRegion(random.nextInt(length + 1), random.nextInt(length + 1))
                    6 -> {
                        val start = random.nextInt(length + 1)
                        val removed = random.nextInt((length - start).coerceAtMost(6) + 1)
                        val splice = TextSplice(start, removed, randomText(random))
                        copy = copy.replaceRange(start, start + removed, splice.inserted)
                        state.applyRemote(listOf(splice))
                    }

                    7 -> if (random.nextBoolean()) state.undo() else state.redo()
                    else -> state.setSelection(TextRange(random.nextInt(length + 1)))
                }
                assertEquals("round $round", copy, state.rawText)
                val current = state.rawText.length
                assertTrue(state.selection.max <= current)
                state.composition?.let { assertTrue(it.max <= current && it.min >= 0) }
            }
        }
    }

    private fun randomText(random: Random) = List(random.nextInt(5)) { "ab \n"[random.nextInt(4)] }.joinToString("")
}
