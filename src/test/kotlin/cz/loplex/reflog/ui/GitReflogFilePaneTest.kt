package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil

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

    /** The two buttons that place the diff pane belong to the tab's toolbar, next to Refresh. */
    fun testTabToolbarCarriesTheDiffPreviewButtons() {
        val ids = idsOf(ActionManager.getInstance().getAction("GitReflog.Toolbar"))

        assertTrue("Preview on the right is not on the tab's toolbar: $ids", "GitReflog.PreviewOnTheRight" in ids)
        assertTrue("Preview at the bottom is not on the tab's toolbar: $ids", "GitReflog.PreviewAtTheBottom" in ids)
    }

    private fun idsOf(action: AnAction): List<String> {
        val actionManager = ActionManager.getInstance()
        val id = actionManager.getId(action)
        val children = (action as? ActionGroup)?.getChildren(null)?.flatMap { idsOf(it) }.orEmpty()
        return listOfNotNull(id) + children
    }
}
