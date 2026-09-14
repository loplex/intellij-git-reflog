package cz.loplex.reflog

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import cz.loplex.reflog.ui.GitReflogContentProvider
import cz.loplex.reflog.ui.GitReflogPanel
import cz.loplex.reflog.ui.countTextFor

/**
 * Covers what only shows up once the descriptor is loaded: the tab has to reach the Git tool window, and the
 * panel has to find the action groups it builds its toolbars and context menus from.
 */
class GitReflogTabTest : BasePlatformTestCase() {

    fun testTabIsContributedToTheGitToolWindow() {
        val tab = ChangesViewContentEP.EP_NAME.getExtensions(project).find { it.tabName == GitReflogPanel.TAB_NAME }

        assertNotNull("No changesViewContent extension named ${GitReflogPanel.TAB_NAME}", tab)
        assertEquals(GitReflogContentProvider::class.java.name, tab!!.className)
        assertFalse("The tab belongs to the Git tool window, not to the Commit one", tab.isInCommitToolWindow)
    }

    fun testActionGroupsTheTabLooksUpExist() {
        val actionManager = ActionManager.getInstance()

        assertNotNull(actionManager.getAction("GitReflog.Toolbar"))
        assertNotNull(actionManager.getAction("GitReflog.ContextMenu"))
        assertNotNull(actionManager.getAction("GitReflog.LoadMore"))
        assertNotNull(actionManager.getAction("GitReflog.ChangesBrowser.Toolbar"))
        assertNotNull(actionManager.getAction("GitReflog.ChangesBrowser.Popup"))
        assertNotNull(actionManager.getAction("GitReflog.PreviewOnTheRight"))
        assertNotNull(actionManager.getAction("GitReflog.PreviewAtTheBottom"))
        assertNotNull(actionManager.getAction("GitReflog.ShowDiff"))
        assertNotNull(actionManager.getAction("GitReflog.CheckoutRevision"))
        assertNotNull(actionManager.getAction("GitReflog.NewBranch"))
        assertNotNull(actionManager.getAction("GitReflog.Reset"))
    }

    /**
     * The file pane borrows these three from the platform rather than declaring its own. A rename on that side
     * would leave the tab with a toolbar and a menu quietly missing half their items.
     */
    fun testPlatformGroupsTheFilePaneBorrowsExist() {
        val actionManager = ActionManager.getInstance()

        assertNotNull(actionManager.getAction("Vcs.RepositoryChangesBrowserToolbar"))
        assertNotNull(actionManager.getAction("Vcs.RepositoryChangesBrowserMenu"))
        assertNotNull(actionManager.getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    }

    /**
     * What the label beside Load More says in each of the four states it can be in.
     *
     * The last of them is why it exists: with everything read and nothing filtered out the label said nothing,
     * so the click that finished the reading took away the button and the count together and left the row with
     * less in it than before.
     */
    fun testTheCountSaysHowMuchOfTheReflogIsOnScreen() {
        // Counts below a thousand throughout: the numbers are formatted for the locale, and a grouping separator
        // would be asserting the locale of whatever machine runs this rather than what the label says.
        assertEquals("Reading more says how far the read got", "Showing 500 of the newest 500 entries read", countTextFor(500, 500, hasMore = true))
        assertEquals("A filter says how much it left", "Showing 12 of 137", countTextFor(12, 137, hasMore = false))
        assertEquals("The whole reflog says that it is the whole reflog", "Showing all 137 entries", countTextFor(137, 137, hasMore = false))
        // An empty reflog is said by the table standing empty with its own words in it.
        assertEquals("An empty reflog is counted twice over", "", countTextFor(0, 0, hasMore = false))
    }

    fun testPanelBuildsWithoutAnyRepository() {
        val panel = GitReflogPanel(project)
        Disposer.register(testRootDisposable, panel)

        assertNull("A project without a Git repository has nothing to show", panel.repository)
    }
}
