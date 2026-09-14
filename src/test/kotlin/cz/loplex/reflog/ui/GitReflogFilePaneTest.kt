package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
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

    private companion object {
        const val PREVIOUS_ANSWER = "what the previous read answered"
    }

    private fun idsOf(action: AnAction): List<String> {
        val actionManager = ActionManager.getInstance()
        val id = actionManager.getId(action)
        val children = (action as? ActionGroup)?.getChildren(null)?.flatMap { idsOf(it) }.orEmpty()
        return listOfNotNull(id) + children
    }
}
