package de.markusressel.kodeeditor.library.compose

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    fun show(animations: KodeEditorAnimations, lineWrapping: Boolean = false, width: Int = 400) {
        rule.setContent {
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
