package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Somebody else types into the first row, or text is revealed for them, with their red caret behind it. Measured on the
 * pixels of each frame, with real text drawing.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class TypingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    private lateinit var state: KodeEditorState
    private lateinit var layout: KodeEditorLayout
    private var reveal: KodeEditorReveal? = null

    /** where the caret of Jane is in the text */
    private val caret = mutableIntStateOf(0)

    /** how long text takes to appear, and a new editor each time [run] changes */
    private val appearMs = mutableIntStateOf(500)
    private val run = mutableIntStateOf(0)

    private fun show(text: String, caretAt: Int, revealing: Boolean = false, appearMs: Int = 500) {
        state = KodeEditorState(text)
        caret.intValue = caretAt
        this.appearMs.intValue = appearMs
        composeRule.setContent { key(run.intValue) { editor(revealing) } }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    /** a new editor with [text], and a fade of [appearMs], in the same composition */
    private fun showAgain(text: String, caretAt: Int, appearMs: Int) {
        composeRule.runOnIdle {
            state = KodeEditorState(text)
            caret.intValue = caretAt
            this.appearMs.intValue = appearMs
            run.intValue++
        }
        repeat(5) { frame() }
    }

    @androidx.compose.runtime.Composable
    private fun editor(revealing: Boolean) {
        run {
            val reveal = if (revealing) rememberKodeEditorReveal(state, "jane").also { reveal = it } else null
            val at = reveal?.head ?: caret.intValue
            KodeEditor(
                modifier = Modifier.size(400.dp, 200.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onTextLayout = {
                    layout = it
                    reveal?.layout = it
                },
                animations = KodeEditorAnimations(appearance = KodeEditorAppearance(durationMs = appearMs.intValue)),
                gutterColumns = emptyList(),
                remoteCursors = listOf(
                    KodeEditorRemoteCursor("jane", listOf(TextRange(at)), Color.Red, Color.Red.copy(alpha = 0.3f)),
                ),
            )
        }
    }

    private fun frame(): PixelMap {
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        return composeRule.onNodeWithTag("editor").captureToImage().toPixelMap()
    }

    /** @return the middle of the red caret in the first row */
    private fun PixelMap.caretX(): Float {
        val y = (layout.lineHeight / 2).toInt()
        val xs = (0 until width).filter { x -> this[x, y].let { it.red > 0.6f && it.green < 0.4f && it.blue < 0.4f } }
        return (xs.first() + xs.last()) / 2f + 0.5f
    }

    /** @return the columns of the first row that have text in them, not counting the caret */
    private fun PixelMap.inkColumns(): List<Int> = (0 until width).filter { x ->
        (0 until layout.lineHeight.toInt()).any { y ->
            val c = this[x, y]
            val gray = abs(c.red - c.green) < 0.1f && abs(c.green - c.blue) < 0.1f
            gray && c.red < 0.8f
        }
    }

    /** times between keys like a person types them: log-normal around 150 ms, from 60 to 400 ms */
    private fun humanGaps(count: Int, random: Random): List<Long> = List(count) {
        val normal = sqrt(-2.0 * ln(1.0 - random.nextDouble())) * kotlin.math.cos(2.0 * Math.PI * random.nextDouble())
        exp(ln(150.0) + 0.4 * normal).toLong().coerceIn(60L, 400L)
    }

    /**
     * Jane types [typed] at [at], one key after the other at the times of a person, and her cursor moves behind each
     *
     * @param onFrame called with each frame and the time since the start
     */
    private fun janeTypes(typed: String, at: Int, onFrame: (PixelMap, Long) -> Unit) {
        val gaps = humanGaps(typed.length, Random(3))
        janePresses(typed.mapIndexed { index, char -> gaps[index] to char }, at, onFrame)
    }

    /**
     * Jane presses [keys] at [at], each a time to wait after it and the character it types, null for backspace, and her
     * cursor moves behind each
     */
    private fun janePresses(keys: List<Pair<Long, Char?>>, at: Int, onFrame: (PixelMap, Long) -> Unit) {
        var time = 0L
        var next = 0L
        var index = 0
        var position = at
        while (index < keys.size || time < next + 300) {
            if (index < keys.size && time >= next) {
                val (gap, char) = keys[index]
                composeRule.runOnIdle {
                    if (char == null) {
                        position--
                        state.applyRemote(listOf(TextSplice(position, 1, "")))
                    } else {
                        state.applyRemote(listOf(TextSplice(position, 0, char.toString())), appear = true, appearTag = "jane")
                        position++
                    }
                    caret.intValue = position
                }
                next = time + gap
                index++
            }
            val image = frame()
            time += 16
            onFrame(image, time)
        }
    }

    @Test
    fun `the text behind what somebody types moves with their caret`() {
        show("first line", 5)
        val tail = ArrayList<Float>()

        janeTypes(" typed", 5) { image, _ ->
            val caretX = image.caretX()
            // the first text right of the caret is the tail, " line"
            val first = image.inkColumns().firstOrNull { it > caretX + 2 } ?: return@janeTypes
            tail += first - caretX
        }

        assertTrue("same distance: $tail", tail.max() - tail.min() <= 2f)
    }

    @Test
    fun `what somebody types shows only where their caret has been`() {
        show("first line", 10)
        val ahead = ArrayList<String>()

        janeTypes(" typed by Jane", 10) { image, time ->
            val caretX = image.caretX()
            val beyond = image.inkColumns().filter { it > caretX + 2 }
            if (beyond.isNotEmpty()) ahead += "$time ms: caret $caretX, text at $beyond"
        }

        assertTrue(ahead.joinToString("\n"), ahead.isEmpty())
    }

    @Test
    fun `revealed text shows only where the caret has been`() {
        show("first line", 10, revealing = true)
        val ahead = ArrayList<String>()

        composeRule.runOnIdle {
            reveal!!.insert(10, " revealed for Jane")
            // where she stays when all of it is there
            caret.intValue = state.text.length
        }
        repeat(40) { frameIndex ->
            val image = frame()
            val caretX = image.caretX()
            val beyond = image.inkColumns().filter { it > caretX + 2 }
            if (beyond.isNotEmpty()) ahead += "frame $frameIndex: caret $caretX, text at $beyond"
        }

        assertTrue(ahead.joinToString("\n"), ahead.isEmpty())
    }

    @Test
    fun `the caret of somebody who types moves at an even speed`() {
        show("first line", 10)
        val xs = ArrayList<Float>()
        var typing = 0L

        janeTypes(" typed by Jane, one key after the other", 10) { image, time ->
            xs += image.caretX()
            typing = time
        }
        // while she types, without the time after her last key
        val speeds = xs.zipWithNext { a, b -> b - a }.dropLast(300 / 16)
        val mean = speeds.average()
        val p95 = speeds.sorted()[speeds.size * 95 / 100]

        assertTrue("$typing ms, forwards: $speeds", speeds.all { it >= -0.5f })
        assertTrue("p95 $p95, mean $mean: $speeds", p95 <= 2.5 * mean)
    }

    @Test
    fun `the text behind somebody who types on does not stop at a key`() {
        show("first line", 10)
        val stops = ArrayList<Long>()
        var last = -1f
        var keyAt = 0
        var lastKeyTime = -1000L

        janeTypes(" typed by Jane, one key after the other", 10) { _, time ->
            val c = composeRule.runOnIdle { caret.intValue }
            val x = composeRule.runOnIdle { (layout as EditorLineLayout).shownCursorRect(c).left }
            if (c != keyAt) {
                // a key right after the one before goes on moving in its first frame
                if (time - lastKeyTime <= STEADY_INSERT_MS && x - last < 0.01f) stops += time
                keyAt = c
                lastKeyTime = time
            }
            last = x
        }

        assertTrue("stops at the keys at $stops ms", stops.isEmpty())
    }

    /** @return how visible each character of the text is, by the appearing ranges */
    private fun alphas(): FloatArray = composeRule.runOnIdle {
        val result = FloatArray(state.text.length) { 1f }
        state.rangeAlphas.forEach { range -> (range.start until range.end).forEach { result[it] = range.item } }
        result
    }

    /** the characters whose alpha changed by more than 0.1 from [before] to [after], which have the same start */
    private fun jumps(before: FloatArray, after: FloatArray, time: Long): List<String> =
        (0 until minOf(before.size, after.size)).mapNotNull { index ->
            val jump = abs(after[index] - before[index])
            if (jump > 0.1f) "$time ms: char $index from ${before[index]} to ${after[index]}" else null
        }

    @Test
    fun `typed text that appears slowly gets visible a bit in each frame`() = typedAppears(1000)

    @Test
    fun `typed text gets visible a bit in each frame`() = typedAppears(500)

    private fun typedAppears(appearMs: Int) {
        show("first line", 10, appearMs = appearMs)
        val jumps = ArrayList<String>()
        var before = alphas()

        janeTypes(" typed by Jane, one key after the other, a long line that goes on", 10) { _, time ->
            val after = alphas()
            jumps += jumps(before, after, time)
            before = after
        }

        assertTrue(jumps.joinToString("\n"), jumps.isEmpty())
    }

    @Test
    fun `revealed text that appears slowly gets visible a bit in each frame`() = revealedAppears(1000)

    @Test
    fun `revealed text gets visible a bit in each frame`() = revealedAppears(500)

    private fun revealedAppears(appearMs: Int) {
        show("first line", 10, revealing = true, appearMs = appearMs)
        var most = 0
        val jumps = ArrayList<String>()
        var before = FloatArray(0)

        composeRule.runOnIdle {
            reveal!!.insert(10, " revealed for Jane, ".repeat(20))
            caret.intValue = state.text.length
        }
        repeat(400) { frameIndex ->
            frame()
            val after = alphas()
            most = maxOf(most, composeRule.runOnIdle { state.rangeAlphas.size })
            jumps += jumps(before, after, frameIndex * 16L)
            // hidden text is not counted, it shows only as it is revealed
            val hidden = composeRule.runOnIdle { reveal!!.head } ?: after.size
            before = after.copyOf(hidden)
        }

        assertTrue(jumps.joinToString("\n"), jumps.isEmpty())
        // about one piece per frame, far from the limit at which pieces become one
        assertTrue("$most pieces", most <= appearMs / 16 + 4)
    }

    /** a person types [text], with a typo after [typoAfter] characters that they remove with backspace */
    private fun typedWithTypo(text: String, typoAfter: Int): List<Pair<Long, Char?>> {
        val gaps = humanGaps(text.length + 6, Random(5))
        val keys = ArrayList<Pair<Long, Char?>>()
        text.take(typoAfter).forEachIndexed { index, char -> keys += gaps[index] to char }
        // two wrong keys, noticed after 350 ms, removed 120 ms apart, then on with the right ones
        keys += gaps[typoAfter] to 'x'
        keys += 350L to 'z'
        keys += 120L to null
        keys += 120L to null
        text.drop(typoAfter).forEachIndexed { index, char -> keys += gaps[typoAfter + 1 + index] to char }
        return keys
    }

    @Test
    fun `the text behind somebody who removes a typo moves with their caret`() {
        show("first line", 5)
        val tail = ArrayList<Float>()

        janePresses(typedWithTypo(" typed", 3), 5) { image, _ ->
            val caretX = image.caretX()
            val first = image.inkColumns().firstOrNull { it > caretX + 2 } ?: return@janePresses
            tail += first - caretX
        }

        assertTrue("same distance: $tail", tail.max() - tail.min() <= 2f)
    }

    @Test
    fun `the caret of somebody who removes a typo moves at an even speed`() {
        show("first line", 10)
        val xs = ArrayList<Float>()

        janePresses(typedWithTypo(" typed by Jane, one key after the other", 12), 10) { image, _ ->
            xs += image.caretX()
        }
        val speeds = xs.zipWithNext { a, b -> abs(b - a) }.dropLast(300 / 16)
        val mean = speeds.average()
        val p95 = speeds.sorted()[speeds.size * 95 / 100]

        assertTrue("p95 $p95, mean $mean: $speeds", p95 <= 2.5 * mean)
    }

    @Test
    fun `text removed by somebody else slides the text behind it, without a jump`() {
        show("first line and more", 11)
        val tail = ArrayList<Float>()

        composeRule.runOnIdle {
            // an agent removes "and " in one change, its cursor stays at the start
            state.applyRemote(listOf(TextSplice(11, 4, "")))
        }
        repeat(12) {
            val image = frame()
            val ink = image.inkColumns()
            // the start of "more", the last word
            tail += ink.filter { it > ink.first() }.let { columns ->
                columns.zipWithNext().lastOrNull { (a, b) -> b - a > 4 }?.second ?: columns.last()
            }.toFloat()
        }

        val steps = tail.zipWithNext { a, b -> a - b }
        assertTrue("moves left: $tail", tail.first() > tail.last() + 10)
        assertTrue("no jump: $steps", steps.all { it < (tail.first() - tail.last()) / 2 })
    }

    @Test
    fun `the caret of somebody who types moves the same with a longer fade`() {
        show("first line", 10, appearMs = 250)
        val short = ArrayList<Float>()
        janeTypes(" typed by Jane", 10) { image, _ -> short += image.caretX() }

        showAgain("first line", 10, appearMs = 1000)
        val long = ArrayList<Float>()
        janeTypes(" typed by Jane", 10) { image, _ -> long += image.caretX() }

        val differences = short.zip(long) { a, b -> abs(a - b) }
        assertTrue("the same way: $short\n$long", short.size == long.size && differences.all { it <= 1f })
    }
}
