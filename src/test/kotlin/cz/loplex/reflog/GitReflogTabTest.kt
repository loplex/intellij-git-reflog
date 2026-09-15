package cz.loplex.reflog

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.ui.content.ContentFactory
import cz.loplex.reflog.ui.GitReflogContentPreloader
import cz.loplex.reflog.ui.GitReflogContentProvider
import cz.loplex.reflog.ui.GitReflogContentVisibilityPredicate
import cz.loplex.reflog.ui.GitReflogDisplayNameSupplier
import javax.swing.JPanel
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
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

    /**
     * The tab is offered to a project that has Git among its active version control systems, and this project
     * has none - so it is not offered here. The other half of the rule, a project that does have Git, needs a
     * repository and is covered in [cz.loplex.reflog.GitReflogTabVisibilityTest].
     */
    fun testTheTabIsNotOfferedToAProjectWithoutGit() {
        assertFalse(
            "The tab is offered to a project with no Git in it",
            GitReflogContentVisibilityPredicate().test(project),
        )
    }

    /** The tab sits right behind the Log, which is the tab it complements rather than replaces. */
    fun testTheTabIsPlacedBehindTheLog() {
        val content = ContentFactory.getInstance().createContent(JPanel(), "Reflog", false)
        Disposer.register(testRootDisposable, content)

        GitReflogContentPreloader().preloadTabContent(content)

        assertEquals(
            ChangesViewContentManager.TabOrderWeight.VCS_LOG.weight + 1,
            content.getUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY),
        )
    }

    /** The name the tab is looked up by has to be the name it is drawn under. */
    fun testTheTabIsNamedWhatThePanelIsLookedUpBy() {
        assertEquals(GitReflogPanel.TAB_NAME, GitReflogDisplayNameSupplier().get())
    }

    /**
     * Load More is drawn by the panel beside the count, not contributed as an action, so that it appears the
     * moment a read comes back rather than whenever a toolbar next gets around to asking its actions.
     *
     * Which also means nothing but the panel can show it: a tab with nothing read has nothing to read more of.
     */
    fun testLoadMoreIsDrawnByThePanelAndStartsHidden() {
        val panel = GitReflogPanel(project)
        Disposer.register(testRootDisposable, panel)

        val link = UIUtil.findComponentOfType(panel, ActionLink::class.java)
        assertNotNull("The tab has no Load More link", link)
        assertEquals("Load More", link!!.text)
        assertFalse("Load More is offered before anything has been read", link.isVisible)
    }

    fun testPanelBuildsWithoutAnyRepository() {
        val panel = GitReflogPanel(project)
        Disposer.register(testRootDisposable, panel)

        assertNull("A project without a Git repository has nothing to show", panel.repository)
    }
}
