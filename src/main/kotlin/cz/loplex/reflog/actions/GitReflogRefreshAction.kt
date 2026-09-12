package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import cz.loplex.reflog.ui.GitReflogDataKeys

/**
 * Re-reads the reflog shown in the tab. The tab also reloads on its own whenever the repository changes; this is
 * for the cases git does not report, such as a reflog rewritten from outside the IDE.
 */
internal class GitReflogRefreshAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(GitReflogDataKeys.PANEL) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.getData(GitReflogDataKeys.PANEL)?.reload()
    }
}
