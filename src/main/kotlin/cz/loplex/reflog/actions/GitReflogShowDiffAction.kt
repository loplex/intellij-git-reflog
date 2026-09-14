package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogDiffMode
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogSelection
import cz.loplex.reflog.readReflogChanges
import cz.loplex.reflog.ui.GitReflogDataKeys
import cz.loplex.reflog.readReflogEntryChanges
import git4idea.repo.GitRepository

/**
 * Opens what the file pane is showing in the diff viewer.
 *
 * Which reading that is belongs to Compare, and this follows it - and says so in its own name, since the pane
 * that would otherwise be the answer is one of the things this tab lets you put away. Any of the other three is
 * a submenu away.
 *
 * It used to read the selected entry against its own parent whatever Compare was set to, which on a checkout or
 * a reset answered a different question than the pane did, and was in any case the same thing the submenu's
 * "Selected Commits" does.
 *
 * Unlike jumping into the Log, this works for commits that no ref points to any more - the resets, amends and
 * rebases that are the very reason to open the reflog - because the changes are read straight from git.
 */
internal class GitReflogShowDiffAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val shown = e.getData(GitReflogDataKeys.DIFF_MODES)?.effective

        // Enabled on there being a comparison on screen at all, which only an empty selection has none of - and
        // where there is nothing to look at, there is nothing to open.
        e.presentation.isEnabled = shown != null
        // Named for the reading it will open. "Show Diff" on its own says which of four only if the file pane is
        // there to be looked at, and the file pane is one of the things this tab lets you put away.
        e.presentation.text = shown
            ?.let { GitReflogBundle.message("reflog.show.diff.as", titleOf(it)) }
            ?: GitReflogBundle.message("action.GitReflog.ShowDiff.text")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = e.getData(GitReflogDataKeys.REPOSITORY) ?: return
        val selection = e.getData(GitReflogDataKeys.SELECTION) ?: return
        val mode = e.getData(GitReflogDataKeys.DIFF_MODES)?.effective ?: return

        showReflogDiff(project, repository, selection, mode)
    }
}

/**
 * Opens one reading of the selected entries in the diff viewer.
 *
 * The file pane shows one reading at a time, the one Compare is set to. This is how the other three are looked at
 * without changing which one the pane is showing - the question "and what would *that* comparison say" being one
 * asked in passing, rather than one worth rearranging the tab for.
 *
 * Greyed for the readings a selection has no answer for, exactly as Compare greys them: what fits is a property
 * of the selection, not of which menu is asking.
 */
internal class GitReflogShowDiffInModeAction(private val mode: GitReflogDiffMode) : DumbAwareAction() {

    init {
        templatePresentation.text = titleOf(mode)
        templatePresentation.description = descriptionOf(mode)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val modes = e.getData(GitReflogDataKeys.DIFF_MODES)
        e.presentation.isEnabled = modes != null && mode in modes.applicable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = e.getData(GitReflogDataKeys.REPOSITORY) ?: return
        val selection = e.getData(GitReflogDataKeys.SELECTION) ?: return

        showReflogDiff(project, repository, selection, mode)
    }
}

/**
 * The four readings, as a submenu under Show Diff.
 *
 * Withheld entirely where nothing fits, which is to say with nothing selected, for the same reason Compare
 * withholds itself there: four greyed readings explain an empty table no better than their absence does.
 */
internal class GitReflogShowDiffModeGroup : ActionGroup(), DumbAware {

    init {
        isPopup = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val modes = e.getData(GitReflogDataKeys.DIFF_MODES)
        e.presentation.isEnabledAndVisible = modes != null && modes.applicable.isNotEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
        GitReflogDiffMode.entries.map { GitReflogShowDiffInModeAction(it) }.toTypedArray()
}

/**
 * Reads what [mode] compares for [selection] and opens it in the diff viewer.
 */
internal fun showReflogDiff(
    project: Project,
    repository: GitRepository,
    selection: GitReflogSelection,
    mode: GitReflogDiffMode,
) {
    val title = GitReflogBundle.message("reflog.diff.mode.progress", titleOf(mode))
    val task = object : Task.Backgroundable(project, title, true) {
        private var changes: List<Change> = emptyList()

        override fun run(indicator: ProgressIndicator) {
            changes = readReflogChanges(project, repository, selection, mode)
        }

        override fun onSuccess() {
            if (ShowDiffAction.canShowDiff(project, changes)) {
                ShowDiffAction.showDiffForChange(project, changes)
            }
            else {
                VcsNotifier.getInstance(project).notifyWarning(
                    EMPTY_DIFF_NOTIFICATION_ID,
                    GitReflogBundle.message("reflog.diff.empty.title"),
                    GitReflogBundle.message("reflog.diff.mode.empty.message", titleOf(mode)),
                )
            }
        }

        override fun onThrowable(error: Throwable) {
            VcsNotifier.getInstance(project).notifyError(
                FAILED_DIFF_NOTIFICATION_ID,
                GitReflogBundle.message("reflog.diff.mode.failed.title", titleOf(mode)),
                error.message.orEmpty(),
            )
        }
    }
    task.queue()
}

private const val EMPTY_DIFF_NOTIFICATION_ID = "git.reflog.diff.empty"
private const val FAILED_DIFF_NOTIFICATION_ID = "git.reflog.diff.failed"
