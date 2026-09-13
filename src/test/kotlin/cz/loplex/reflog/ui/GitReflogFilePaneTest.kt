package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Covers that the toolbars really end up carrying what the tab builds for them. The groups are looked up by id
 * and assembled in XML, so a typo would leave a toolbar silently bare rather than fail anything.
 */
class GitReflogFilePaneTest : BasePlatformTestCase() {

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
