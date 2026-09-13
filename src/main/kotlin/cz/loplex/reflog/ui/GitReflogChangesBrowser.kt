package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser

/**
 * The file pane, with the toolbar and context menu the Log's own file pane has.
 *
 * The platform's browser brings Show Diff and Group By; the groups added here are the ones the Log adds on top -
 * Revert and Show History for Revision on the toolbar, the whole repository menu on right click - and they need
 * nothing from the tab beyond the changes and the revision it already publishes into the data context.
 *
 * The diff pane is not placed from here: its two buttons sit on the tab's own toolbar, next to Refresh.
 */
internal class GitReflogChangesBrowser(project: Project) : SimpleAsyncChangesBrowser(project, false, false) {

    override fun createToolbarActions(): List<AnAction> =
        super.createToolbarActions() + ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID)

    override fun createPopupMenuActions(): List<AnAction> =
        super.createPopupMenuActions() + ActionManager.getInstance().getAction(POPUP_GROUP_ID)

    private companion object {
        const val TOOLBAR_GROUP_ID = "GitReflog.ChangesBrowser.Toolbar"
        const val POPUP_GROUP_ID = "GitReflog.ChangesBrowser.Popup"
    }
}
