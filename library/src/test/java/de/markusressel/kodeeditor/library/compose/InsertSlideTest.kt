package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAnimations
import de.markusressel.kodeeditor.library.compose.animation.KodeEditorAppearance
import de.markusressel.kodeeditor.library.compose.layout.EditorLineLayout
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
import de.markusressel.kodeeditor.library.compose.state.TextSplice
import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.StyleFactory
import de.markusressel.kodehighlighter.core.colorscheme.ColorScheme
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An editor whose text somebody else inserts into, with the frame clock paused */
private class InsertScene(private val rule: ComposeContentTestRule, text: String) {
    private val noRules = object : LanguageRuleBook {
        override fun getRules() = emptyList<LanguageRule>()
    }

    private val noColors = object : ColorScheme<SpanStyle> {
        override fun getStyles(type: LanguageRule) = emptySet<StyleFactory<SpanStyle>>()
    }

    val state = KodeEditorState(text)
    var layout: EditorLineLayout? = null

    /** whether a second editor shows the same state */
    var showOther by mutableStateOf(false)

    fun show(animations: KodeEditorAnimations, lineWrapping: Boolean = false, width: Int = 400) {
        rule.setContent {
            if (showOther) {
                KodeEditor(
                    modifier = Modifier.size(width.dp, 100.dp),
                    state = state,
                    languageRuleBook = noRules,
                    colorScheme = noColors,
                    animations = animations,
                    gutterColumns = emptyList(),
                )
            }
            KodeEditor(
                modifier = Modifier.size(width.dp, 400.dp),
                state = state,
                languageRuleBook = noRules,
                colorScheme = noColors,
                onTextLayout = { layout = it as EditorLineLayout },
                animations = animations,
                gutterColumns = emptyList(),
                lineWrapping = lineWrapping,
            )
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    /** where the text at [offset] is drawn now */
    fun shown(offset: Int) = layout!!.shownCursorRect(offset).topLeft

    /** where the text at [offset] is drawn once nothing moves anymore */
    fun settled(offset: Int) = layout!!.getCursorRect(offset).topLeft

    /** lets one frame pass, see the scene of the remote cursors */
    fun frame() {
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()
    }

    fun settle() {
        repeat(5) { frame() }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
    }
}

// real text measurement, the legacy one has no realistic widths of characters
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class InsertSlideTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = KodeEditorAnimations(appearance = KodeEditorAppearance())

    @Test
    fun `the text behind what somebody else inserts slides out of the way`() {
        val scene = InsertScene(composeRule, "hello world")
        scene.show(appearance)
        val before = scene.shown(6)

        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(6, 0, "brave ")), appear = true) }
        scene.frame()
        scene.frame()
        scene.frame()
        val moving = scene.shown(12)
        val after = scene.settled(12)
        scene.settle()

        assertTrue("on its way: $before $moving $after", moving.x > before.x + 1 && moving.x < after.x - 1)
        assertEquals(before.y, moving.y)
        assertEquals(after, scene.shown(12))
    }

    @Test
    fun `the text behind an insertion slides into the next row when the line breaks anew`() {
        val scene = InsertScene(composeRule, "aaaa bbbb cccc dddd")
        scene.show(appearance, lineWrapping = true, width = 150)
        val last = scene.state.text.length - 1
        val before = scene.shown(last)

        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(5, 0, "xxxx xxxx xxxx ")), appear = true) }
        val moved = last + 15
        val after = scene.settled(moved)
        scene.frame()
        scene.frame()
        scene.frame()
        val moving = scene.shown(moved)
        scene.settle()

        assertTrue("it breaks into another row: $before $after", after.y > before.y)
        assertTrue("on its way: $before $moving $after", moving.y > before.y + 1 && moving.y < after.y - 1)
        assertEquals(after, scene.shown(moved))
    }

    @Test
    fun `the lines below inserted lines slide down`() {
        val scene = InsertScene(composeRule, "first\nsecond")
        scene.show(appearance)
        val before = scene.shown(6)

        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(6, 0, "new\n")), appear = true) }
        scene.frame()
        scene.frame()
        scene.frame()
        val moving = scene.shown(10)
        val after = scene.settled(10)

        assertTrue("on its way: $before $moving $after", moving.y > before.y + 1 && moving.y < after.y - 1)
    }

    @Test
    fun `an editor that goes away does not stop the text of another one on the same state from sliding`() {
        val scene = InsertScene(composeRule, "hello world")
        scene.showOther = true
        scene.show(appearance)
        composeRule.runOnIdle { scene.showOther = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val before = scene.shown(6)

        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(6, 0, "brave ")), appear = true) }
        scene.frame()
        scene.frame()
        scene.frame()
        val moving = scene.shown(12)
        val after = scene.settled(12)

        assertTrue("on its way: $before $moving $after", moving.x > before.x + 1 && moving.x < after.x - 1)
        assertTrue("it still appears", scene.state.rangeAlphas.isNotEmpty())
    }

    @Test
    fun `the text behind somebody who types keeps moving on when an agent writes elsewhere`() {
        val scene = InsertScene(composeRule, "aaaa rest\nbbbb")
        scene.show(appearance)
        fun key(at: Int) {
            composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(at, 0, "x")), appear = true) }
        }
        key(4)
        repeat(6) { scene.frame() }
        key(5)
        // that key has moved the text to its place
        repeat(10) { scene.frame() }
        // a chunk of an agent at the end of the next line, between two keys
        composeRule.runOnIdle {
            scene.state.applyRemote(listOf(TextSplice(scene.state.text.length, 0, " chunk")), appear = true)
        }
        repeat(3) { scene.frame() }
        val before = scene.shown(7)

        key(6)
        scene.frame()
        val moving = scene.shown(8)

        // typed on, it moves in its first frame already, like the keys before
        assertTrue("moves on at once: $before $moving", moving.x > before.x + 0.3f)
    }

    @Test
    fun `text the user types does not slide`() {
        val scene = InsertScene(composeRule, "hello world")
        scene.show(appearance)

        composeRule.runOnIdle { scene.state.edit("hello brave world", TextRange(12)) }
        scene.frame()

        assertEquals(scene.settled(12), scene.shown(12))
    }

    @Test
    fun `without an appearance the text behind an insertion jumps`() {
        val scene = InsertScene(composeRule, "hello world")
        scene.show(KodeEditorAnimations(appearance = null))

        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(6, 0, "brave ")), appear = true) }
        scene.frame()

        assertEquals(scene.settled(12), scene.shown(12))
    }

    @Test
    fun `an insertion after a key of the user does not start from the slide before the key`() {
        val scene = InsertScene(composeRule, "hello world")
        scene.show(appearance)

        composeRule.runOnIdle {
            // all in one frame: somebody inserts, the user types at the end, somebody inserts behind that
            scene.state.applyRemote(listOf(TextSplice(0, 0, "abc ")), appear = true)
            scene.state.setSelection(TextRange(scene.state.text.length))
            scene.state.replaceSelection("xyz")
            scene.state.applyRemote(listOf(TextSplice(scene.state.text.length, 0, " def")), appear = true)
        }
        val end = scene.state.text.length
        val moving = scene.shown(end)
        scene.settle()

        assertEquals(scene.settled(end).y, moving.y)
        assertEquals(scene.settled(end), scene.shown(end))
    }

    @Test
    fun `the user types while others insert in the middle and at the end`() = typeWhileOthersInsert(wrapping = false)

    @Test
    fun `the user types while others insert, with wrapping`() = typeWhileOthersInsert(wrapping = true)

    /** like the app with an agent and another user, all in one frame, about 20 times in a second */
    private fun typeWhileOthersInsert(wrapping: Boolean) {
        run {
            val scene = InsertScene(composeRule, "first line of the text\nsecond line, where the user types")
            scene.show(appearance, lineWrapping = wrapping, width = 200)
            repeat(20) { round ->
                composeRule.runOnIdle {
                    // somebody in the middle of the first line, the user's key at the end, an agent behind it
                    scene.state.applyRemote(listOf(TextSplice(6, 0, "x")), appear = true)
                    scene.state.setSelection(TextRange(scene.state.text.length))
                    scene.state.replaceSelection("k")
                    val end = scene.state.text.length
                    scene.state.applyRemote(listOf(TextSplice(end, 0, " chunk $round")), appear = true)
                }
                if (round % 3 == 0) scene.frame()
                val end = scene.state.text.length
                (0..end).forEach { scene.shown(it) }
            }
            scene.settle()
            val end = scene.state.text.length
            assertEquals("wrapping $wrapping", scene.settled(end), scene.shown(end))
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class InsertSlideWithoutAnimationsTest {

    @get:Rule
    val composeRule = createComposeRule(
        effectContext = object : MotionDurationScale {
            override val scaleFactor = 0f
        },
    )

    @Test
    fun `the text behind an insertion jumps when the system turns animations off`() {
        val scene = InsertScene(composeRule, "hello world")
        scene.show(KodeEditorAnimations(appearance = KodeEditorAppearance()))

        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(6, 0, "brave ")), appear = true) }
        scene.frame()

        assertEquals(scene.settled(12), scene.shown(12))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class InsertSlideAnimationsTurnedOffTest {

    /** the system setting, which the user can change while the editor is shown */
    private val motion = object : MotionDurationScale {
        override var scaleFactor = 1f
    }

    @get:Rule
    val composeRule = createComposeRule(effectContext = motion)

    @Test
    fun `turning animations off while the editor is shown makes changes of others jump and shows appearing text`() {
        val scene = InsertScene(composeRule, "hello world")
        scene.show(KodeEditorAnimations(appearance = KodeEditorAppearance()))
        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(0, 0, "a ")), appear = true) }
        scene.frame()
        assertTrue("text appears with animations on", scene.state.rangeAlphas.isNotEmpty())

        motion.scaleFactor = 0f
        scene.frame()
        assertTrue("appearing text shows at once", scene.state.rangeAlphas.isEmpty())
        composeRule.runOnIdle { scene.state.applyRemote(listOf(TextSplice(8, 0, "brave ")), appear = true) }
        scene.frame()

        assertEquals(scene.settled(14), scene.shown(14))
    }
}
