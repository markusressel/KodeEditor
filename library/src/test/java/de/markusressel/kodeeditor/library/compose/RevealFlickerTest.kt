package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.SpanStyle
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

// real text drawing, to see what is drawn and not only what the state says
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class RevealFlickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    /** how visible each character of the text is drawn, from the range styles and the appearing ranges */
    private fun alphas(state: KodeEditorState): FloatArray {
        val alphas = FloatArray(state.text.length) { 1f }
        state.rangeAlphas.forEach { range -> for (i in range.start until range.end) alphas[i] = minOf(alphas[i], range.item) }
        state.rangeStyles.filter { it.item.color == Color.Transparent }.forEach { range ->
            for (i in range.start until range.end) alphas[i] = 0f
        }
        return alphas
    }

    /**
     * lets an agent write chunks at the start of the text, quickly, in bursts and after pauses, and calls [onFrame]
     * after each frame
     *
     * @return how long the text of the agent is
     */
    private fun agentRun(
        chunkCount: Int,
        onFrame: (state: KodeEditorState, layout: KodeEditorLayout, written: Int) -> Unit,
    ): Pair<KodeEditorState, Int> {
        val state = KodeEditorState("\nthe text below")
        lateinit var reveal: KodeEditorReveal
        var layout: KodeEditorLayout? = null
        composeRule.setContent {
            reveal = rememberKodeEditorReveal(state, "agent")
            KodeEditor(
                modifier = Modifier.size(400.dp, 800.dp).testTag("editor"),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onTextLayout = {
                    reveal.layout = it
                    layout = it
                },
                animations = KodeEditorAnimations(appearance = KodeEditorAppearance()),
                gutterColumns = emptyList(),
                lineWrapping = true,
            )
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        val random = Random(33)
        val words = "the agent writes a long answer in chunks of a few words each".split(' ')
        var nextAt = 0L
        var written = 0
        var time = 0L
        var chunks = 0
        while (chunks < chunkCount || composeRule.runOnIdle { reveal.isRevealing }) {
            if (chunks < chunkCount && time >= nextAt) {
                var chunk = ""
                while (chunk.length < 15) chunk += words.random(random) + " "
                if (chunks % 7 == 3) chunk += "\n"
                val at = written
                composeRule.runOnIdle { reveal.insert(at, chunk) }
                written += chunk.length
                chunks++
                nextAt = time + when (random.nextInt(10)) {
                    0, 1 -> random.nextLong(25, 51)
                    2 -> random.nextLong(1000, 1501)
                    else -> random.nextLong(100, 351)
                }
            }
            Snapshot.sendApplyNotifications()
            composeRule.mainClock.advanceTimeByFrame()
            time += 16
            onFrame(state, layout!!, written)
        }
        repeat(30) { composeRule.mainClock.advanceTimeByFrame() }
        onFrame(state, layout!!, written)
        return state to written
    }

    @Test
    fun `characters of chunks that arrive while others are revealed only ever get more visible`() {
        var last = FloatArray(0)
        val problems = ArrayList<String>()
        var frame = 0
        val (state, written) = agentRun(60) { state, _, written ->
            frame++
            val now = composeRule.runOnIdle { alphas(state) }
            for (i in 0 until minOf(last.size, written)) {
                if (now[i] < last[i] - 0.001f) problems += "frame $frame: char $i ${last[i]} -> ${now[i]}"
            }
            last = now.copyOf(written)
        }
        val end = composeRule.runOnIdle { alphas(state) }

        assertTrue("less visible:\n${problems.take(20).joinToString("\n")}", problems.isEmpty())
        assertTrue("all of it shown in the end", (0 until written).all { end[it] == 1f })
    }

    @Test
    fun `characters of chunks that arrive while others are revealed are drawn more visible in each frame`() {
        // how much ink each character of the agent has in each frame, and whether it is hidden
        val inks = ArrayList<FloatArray>()
        val hidden = ArrayList<BooleanArray>()
        agentRun(20) { state, layout, written ->
            val alphas = composeRule.runOnIdle { alphas(state) }
            hidden += BooleanArray(written) { alphas[it] == 0f }
            val bitmap = composeRule.onNodeWithTag("editor").captureToImage()
            val image = bitmap.toPixelMap()
            val background = image[image.width - 1, image.height - 1]
            // the text starts behind a padding
            val padding = composeRule.density.run { TEXT_START_PADDING.toPx() }
            inks += FloatArray(written) { i ->
                val from = layout.getCursorRect(i)
                val to = layout.getCursorRect(i + 1)
                // the last one of a row, or a line break
                if (to.top != from.top || to.left <= from.left) return@FloatArray 0f
                var ink = 0f
                // without the columns it shares with its neighbors, which their clips cut through
                for (x in ceil(from.left + padding).toInt() until floor(to.left + padding).toInt()) {
                    for (y in from.top.toInt() until from.bottom.toInt().coerceAtMost(image.height)) {
                        val c = image[x, y]
                        ink += abs(c.red - background.red) + abs(c.green - background.green) + abs(c.blue - background.blue)
                    }
                }
                ink
            }
        }
        val full = inks.last()
        val problems = ArrayList<String>()
        for (frame in 1 until inks.size) {
            val before = inks[frame - 1]
            val now = inks[frame]
            for (i in 0 until minOf(before.size, now.size)) {
                // a hidden one is not drawn, other text that moves out of its way may be where it will be
                if (full[i] < 1f || hidden[frame - 1][i]) continue
                if (now[i] < before[i] - 0.1f * full[i]) {
                    problems += "frame $frame: char $i ${before[i] / full[i]} -> ${now[i] / full[i]}"
                }
            }
        }

        assertTrue("drawn less visible:\n${problems.take(20).joinToString("\n")}", problems.isEmpty())
    }
}
