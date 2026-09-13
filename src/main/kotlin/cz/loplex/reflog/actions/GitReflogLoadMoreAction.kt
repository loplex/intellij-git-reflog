package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import cz.loplex.reflog.ui.GitReflogDataKeys

/**
 * Reads one more page of older records.
 *
 * Hidden while the whole reflog is on screen, which is the usual case - it only appears once a read came back
 * full, and with it the possibility that what the user is looking for is older than what was read. The filters
 * cannot help there: they run over the entries already read.
 */
internal class GitReflogLoadMoreAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(GitReflogDataKeys.HAS_MORE) == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(GitReflogDataKeys.PANEL)?.loadMore()
    }
}
