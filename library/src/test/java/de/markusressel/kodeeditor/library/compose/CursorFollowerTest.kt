package de.markusressel.kodeeditor.library.compose

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorFollowerTest {

    private val follower = CursorFollower().apply { shouldFollow(TextRange(1), focused = true) }

    @Test
    fun `follows the cursor while focused`() {
        assertTrue(follower.shouldFollow(TextRange(2), focused = true))
    }

    @Test
    fun `does not follow without focus`() {
        assertFalse(follower.shouldFollow(TextRange(2), focused = false))
    }

    @Test
    fun `a size change keeps the view the user moved to`() {
        follower.onUserMovedView()
        assertFalse(follower.shouldFollow(TextRange(1), focused = true))
    }

    @Test
    fun `a size change follows when the user did not move the view`() {
        assertTrue(follower.shouldFollow(TextRange(1), focused = true))
    }

    @Test
    fun `moving the cursor follows again`() {
        follower.onUserMovedView()
        follower.shouldFollow(TextRange(1), focused = true)
        assertTrue(follower.shouldFollow(TextRange(2), focused = true))
    }

    @Test
    fun `getting the focus follows again`() {
        follower.onUserMovedView()
        follower.shouldFollow(TextRange(1), focused = false)
        assertTrue(follower.shouldFollow(TextRange(1), focused = true))
    }
}
