package cz.loplex.reflog.actions

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogEntry
import git4idea.changes.GitChangeUtils
import git4idea.repo.GitRepository

/**
 * Shows what the commit behind the selected reflog entry changed.
 *
 * Unlike jumping into the Log, this works for commits that no ref points to any more - the resets, amends and
 * rebases that are the very reason to open the reflog - because the changes are read straight from `git show`.
 */
internal class GitReflogShowDiffAction : GitReflogEntryAction() {

    override fun perform(selection: GitReflogSelection) {
        showReflogEntryDiff(selection.project, selection.repository, selection.entry)
    }
}

/**
 * Loads the changes of the commit [entry] points at and opens them in the diff viewer.
 */
internal fun showReflogEntryDiff(project: Project, repository: GitRepository, entry: GitReflogEntry) {
    val task = object : Task.Backgroundable(
        project,
        GitReflogBundle.message("reflog.diff.progress", entry.shortHash),
        true,
    ) {
        private var changes: List<Change> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            // skipDiffsForMerge=true keeps a merge commit comparable against its first parent instead of failing.
            changes = GitChangeUtils
                .getRevisionChanges(project, repository.root, entry.hash, true, false, false)
                .changes
                .toList()
        }

        override fun onSuccess() {
            if (ShowDiffAction.canShowDiff(project, changes)) {
                ShowDiffAction.showDiffForChange(project, changes)
            }
            else {
                VcsNotifier.getInstance(project).notifyWarning(
                    EMPTY_DIFF_NOTIFICATION_ID,
                    GitReflogBundle.message("reflog.diff.empty.title"),
                    GitReflogBundle.message("reflog.diff.empty.message", entry.shortHash),
                )
            }
        }

        override fun onThrowable(error: Throwable) {
            VcsNotifier.getInstance(project).notifyError(
                FAILED_DIFF_NOTIFICATION_ID,
                GitReflogBundle.message("reflog.diff.failed.title", entry.shortHash),
                error.message.orEmpty(),
            )
        }
    }
    task.queue()
}

private const val EMPTY_DIFF_NOTIFICATION_ID = "git.reflog.diff.empty"
private const val FAILED_DIFF_NOTIFICATION_ID = "git.reflog.diff.failed"
