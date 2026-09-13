package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.ui.GitReflogDataKeys
import git4idea.repo.GitRepository

/** The one reflog entry an action works on, together with where it was read from. */
internal data class GitReflogSelection(
    val project: Project,
    val repository: GitRepository,
    val entry: GitReflogEntry,
)

/**
 * Base of the actions that act on a single selected reflog entry.
 *
 * Every one of them needs the same three things out of the data context and is disabled without them, which is
 * also what keeps the actions usable from the context menu and from the Find Action dialog alike.
 */
internal abstract class GitReflogEntryAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    final override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selectionOf(e) != null
    }

    final override fun actionPerformed(e: AnActionEvent) {
        perform(selectionOf(e) ?: return)
    }

    protected abstract fun perform(selection: GitReflogSelection)

    private fun selectionOf(e: AnActionEvent): GitReflogSelection? {
        val project = e.project ?: return null
        val repository = e.getData(GitReflogDataKeys.REPOSITORY) ?: return null
        val entry = e.getData(GitReflogDataKeys.SELECTED_ENTRIES)?.singleOrNull() ?: return null
        return GitReflogSelection(project, repository, entry)
    }
}
