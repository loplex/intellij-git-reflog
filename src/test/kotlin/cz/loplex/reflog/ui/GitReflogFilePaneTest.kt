package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.ui.OnePixelSplitter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import cz.loplex.reflog.GitReflogDiffMode
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogRef
import cz.loplex.reflog.GitReflogSelection
import javax.swing.JPanel

/**
 * Covers that the toolbars really end up carrying what the tab builds for them. The groups are looked up by id
 * and assembled in XML, so a typo would leave a toolbar silently bare rather than fail anything.
 */
class GitReflogFilePaneTest : BasePlatformTestCase() {

    fun testFilePaneToolbarCarriesTheRepositoryActions() {
        val panel = GitReflogPanel(project)
        Disposer.register(testRootDisposable, panel)

        val browser = UIUtil.findComponentOfType(panel, ChangesBrowserBase::class.java)
        assertNotNull("The tab has no file pane", browser)

        val ids = idsOf(browser!!.toolbar.actionGroup)
        assertTrue("Revert is not on the file pane's toolbar: $ids", "Vcs.RevertSelectedChanges" in ids)
        assertTrue("Group By is not on the file pane's toolbar: $ids", "ChangesView.GroupBy" in ids)
    }

    /** Compare is offered in both places a file pane offers anything: its toolbar and its context menu. */
    fun testBothFilePaneMenusOfferTheComparisons() {
        val toolbar = idsOf(ActionManager.getInstance().getAction("GitReflog.ChangesBrowser.Toolbar"))
        assertTrue("Compare is not on the file pane's toolbar: $toolbar", "GitReflog.DiffMode" in toolbar)

        val popup = idsOf(ActionManager.getInstance().getAction("GitReflog.ChangesBrowser.Popup"))
        assertTrue("Compare is not on the file pane's context menu: $popup", "GitReflog.DiffModes" in popup)

        // The entries are selected in the table, so that is where the question is asked as often as not.
        val table = idsOf(ActionManager.getInstance().getAction("GitReflog.ContextMenu"))
        assertTrue("Compare is not on the table's context menu: $table", "GitReflog.DiffModes" in table)
    }

    /** What is on screen below the table is placed from the tab's toolbar, next to Refresh. */
    fun testTabToolbarCarriesTheButtonsThatPlaceThePanes() {
        val ids = idsOf(ActionManager.getInstance().getAction("GitReflog.Toolbar"))

        assertTrue("Show Changed Files is not on the tab's toolbar: $ids", "GitReflog.ShowFiles" in ids)
        assertTrue("Preview on the right is not on the tab's toolbar: $ids", "GitReflog.PreviewOnTheRight" in ids)
        assertTrue("Preview at the bottom is not on the tab's toolbar: $ids", "GitReflog.PreviewAtTheBottom" in ids)
    }

    /** Either pane can be put away on its own, and the table keeps the room when both are. */
    fun testEitherPaneCanBePutAwayOnItsOwn() {
        val panel = GitReflogPanel(project)
        Disposer.register(testRootDisposable, panel)

        panel.isFilePaneVisible = false
        assertNull("The file pane is still in the tab", UIUtil.findComponentOfType(panel, ChangesBrowserBase::class.java))

        panel.isFilePaneVisible = true
        assertNotNull("The file pane did not come back", UIUtil.findComponentOfType(panel, ChangesBrowserBase::class.java))
    }

    /**
     * A read says that it is under way over the list rather than in place of it.
     *
     * Saying so by emptying the list costs the answer already on screen, and the read runs on every step through
     * the table - which is what made the pane blink its way through a walk of the reflog.
     */
    fun testAReadDoesNotEmptyTheListToSayItIsUnderWay() {
        val pane = GitReflogChangesPanel(project, JPanel())
        Disposer.register(testRootDisposable, pane)
        val browser = UIUtil.findComponentOfType(pane, ChangesBrowserBase::class.java)
        assertNotNull("The pane has no file list", browser)

        pane.showEmptyText(PREVIOUS_ANSWER)
        pane.startLoading()

        assertEquals(
            "Starting a read wrote over what the pane was showing",
            PREVIOUS_ANSWER,
            browser!!.viewer.emptyText.text,
        )
    }

    /**
     * Neither side of the splitter can be dragged shut.
     *
     * Dragged to the edge the file pane closed to nothing, which leaves a handle where a pane had been and no
     * way to tell what became of it - where the toolbar has a button for putting the pane away that says so
     * plainly, and remembers it for the next session besides.
     */
    fun testNeitherPaneCanBeDraggedShut() {
        val pane = GitReflogChangesPanel(project, JPanel())
        Disposer.register(testRootDisposable, pane)

        val splitter = UIUtil.findComponentOfType(pane, OnePixelSplitter::class.java)
        assertNotNull("The tab has no splitter between the table and the file pane", splitter)
        assertTrue("The splitter would close a pane to nothing", splitter!!.isHonorMinimumSize)

        assertTrue(
            "The table may be dragged shut: ${splitter.firstComponent.minimumSize}",
            splitter.firstComponent.minimumSize.width > 0,
        )
        assertTrue(
            "The file pane may be dragged shut: ${splitter.secondComponent.minimumSize}",
            splitter.secondComponent.minimumSize.width > 0,
        )
    }

    /**
     * A movement that left the ref where it found it is an answer, not a fault - and it used to be told as one,
     * the pane naming one and the same hash on both sides of "nothing changed between".
     *
     * It happens for real twice over: a checkout between two branches that stand on the same commit, and the
     * reset `git stash` makes internally once it has put the work away.
     */
    fun testAMovementThatStayedPutIsSaidInWordsRatherThanAsAHashTwice() {
        // Two movements of HEAD that both left it at the same commit, as a checkout between two branches
        // standing on one commit does.
        val entries = listOf(entry("HEAD@{0}", STAYED), entry("HEAD@{1}", STAYED), entry("HEAD@{2}", ELSEWHERE))

        val step = selection(entries, entries[0])
        assertEquals(
            "Nothing moved: HEAD stood at 8dede56f both before and after",
            emptyChangesTextFor(step, GitReflogDiffMode.REFLOG_STEP),
        )

        val between = selection(entries, entries[0], entries[1])
        assertEquals(
            "Nothing to compare: both selected states are 8dede56f",
            emptyChangesTextFor(between, GitReflogDiffMode.BETWEEN_SELECTED),
        )
    }

    /** The two states a movement did reach are still named, which is what tells one comparison from another. */
    fun testAnEmptyComparisonOfTwoStatesNamesBothOfThem() {
        val entries = listOf(entry("HEAD@{0}", STAYED), entry("HEAD@{1}", ELSEWHERE))
        val selection = selection(entries, entries[0])

        assertEquals(
            "Nothing changed between 3f0a91c2 and 8dede56f",
            emptyChangesTextFor(selection, GitReflogDiffMode.REFLOG_STEP),
        )
        assertEquals(
            "The working tree matches 8dede56f",
            emptyChangesTextFor(selection, GitReflogDiffMode.WORKING_TREE),
        )
        // The one mode with no recorded state on either side falls back to naming the commit itself.
        assertEquals(
            "Commit 8dede56f changes nothing against its parent",
            emptyChangesTextFor(selection, GitReflogDiffMode.UNION),
        )
    }

    private fun selection(entries: List<GitReflogEntry>, vararg selected: GitReflogEntry) =
        GitReflogSelection(GitReflogRef.HEAD, entries, selected.toList())

    private fun entry(selector: String, hash: String) = GitReflogEntry(
        selector = selector,
        hash = hash,
        timestamp = 0L,
        action = "checkout",
        description = "moving from master to feature",
        author = "Alex Smith",
        subject = "Parse the separator",
    )

    private companion object {
        const val PREVIOUS_ANSWER = "what the previous read answered"

        /** Hashes are shortened to eight characters, so they are written long enough here to be shortened. */
        const val STAYED = "8dede56f1a2b3c4d"
        const val ELSEWHERE = "3f0a91c2b8e7d6a5"
    }

    private fun idsOf(action: AnAction): List<String> {
        val actionManager = ActionManager.getInstance()
        val id = actionManager.getId(action)
        val children = (action as? ActionGroup)?.getChildren(null)?.flatMap { idsOf(it) }.orEmpty()
        return listOfNotNull(id) + children
    }
}
