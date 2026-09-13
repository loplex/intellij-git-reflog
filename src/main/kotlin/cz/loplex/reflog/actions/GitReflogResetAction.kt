package cz.loplex.reflog.actions

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.ui.GitReflogResetDialog
import git4idea.reset.GitResetOperation

/**
 * Resets the current branch to the commit of the selected entry - the undo for a reset, a rebase or an amend that
 * went the wrong way, since the state to return to is the very thing the reflog recorded.
 *
 * Always resets the branch the repository is on, whichever ref's reflog is being shown, which is what the dialog
 * names.
 */
internal class GitReflogResetAction : GitReflogEntryAction() {

    override fun perform(selection: GitReflogSelection) {
        val project = selection.project
        val repository = selection.repository
        val entry = selection.entry

        val dialog = GitReflogResetDialog(
            project,
            // A repository on a detached HEAD has no branch name to show, and resetting it is still meaningful.
            repository.currentBranchName ?: GitReflogBundle.message("reflog.reset.target.detached"),
            entry.shortHash,
        )
        if (!dialog.showAndGet()) return

        object : Task.Backgroundable(project, GitReflogBundle.message("reflog.reset.progress", entry.shortHash), true) {
            override fun run(indicator: ProgressIndicator) {
                // The default presentation holds git4idea's own bundle keys, so the progress, the notifications
                // and the local history entry read exactly as they do for a reset started from the Log.
                GitResetOperation(
                    project,
                    mapOf(repository to entry.hash),
                    dialog.mode,
                    indicator,
                    GitResetOperation.OperationPresentation(),
                ).execute()
            }
        }.queue()
    }
}
