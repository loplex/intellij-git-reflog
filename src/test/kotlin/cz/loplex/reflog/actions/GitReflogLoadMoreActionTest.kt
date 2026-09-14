package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import cz.loplex.reflog.ui.GitReflogDataKeys

/**
 * Covers what the toolbar makes of Load More, which is the only action of the tab drawn as its name rather than
 * as an icon - and the only one whose being there at all is itself the message.
 */
class GitReflogLoadMoreActionTest : BasePlatformTestCase() {

    /** Older records left unread is the whole reason the action exists, so that is when it is there. */
    fun `test Load More is offered while records remain unread`() {
        assertTrue(
            "Load More is not offered with a page left to read",
            updated(GitReflogLoadMoreAction(), contextWithMore(true)).isEnabledAndVisible,
        )
    }

    /**
     * Gone rather than greyed once the whole reflog is on screen: a greyed button says "not now", where there is
     * nothing more to read at all. What the click took away is said instead by the count beside it, which then
     * reads that this is all of it.
     */
    fun `test Load More goes away once the whole reflog is read`() {
        assertFalse(
            "Load More stayed on the toolbar with nothing left to read",
            updated(GitReflogLoadMoreAction(), contextWithMore(false)).isVisible,
        )
    }

    /** Outside the tab there is no reflog to read more of. */
    fun `test Load More is not offered away from the tab`() {
        assertFalse(
            "Load More is offered with no reflog in the context",
            updated(GitReflogLoadMoreAction(), SimpleDataContext.EMPTY_CONTEXT).isVisible,
        )
    }

    /**
     * A toolbar draws an action with no icon as an empty button unless told to draw its text, and an empty button
     * is exactly what this one must not be: its name is the only thing saying that a page was left unread.
     */
    fun `test Load More asks the toolbar for its name rather than an icon`() {
        val presentation = updated(GitReflogLoadMoreAction(), contextWithMore(true))

        assertEquals(
            "Load More would be drawn as an empty button",
            true,
            presentation.getClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR),
        )
    }

    private fun contextWithMore(hasMore: Boolean) =
        SimpleDataContext.getSimpleContext(GitReflogDataKeys.HAS_MORE, hasMore)
}
