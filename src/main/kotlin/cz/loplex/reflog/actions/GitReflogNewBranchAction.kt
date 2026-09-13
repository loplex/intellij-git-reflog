package cz.loplex.reflog.actions

import com.intellij.openapi.ui.Messages
import cz.loplex.reflog.GitReflogBundle
import git4idea.branch.GitBrancher
import git4idea.validators.GitNewBranchNameValidator

/**
 * Creates a branch at the commit of the selected entry and checks it out - the usual way of rescuing work a reset
 * or a rebase left unreachable, because a branch keeps the commit alive past the expiry of the reflog.
 *
 * The name is validated by git4idea's own validator, so it is rejected here for the same reasons git would reject
 * it, and the branch is then created by `GitBrancher` with its handling of local changes.
 */
internal class GitReflogNewBranchAction : GitReflogEntryAction() {

    override fun perform(selection: GitReflogSelection) {
        val repositories = listOf(selection.repository)
        val name = Messages.showInputDialog(
            selection.project,
            GitReflogBundle.message("reflog.branch.dialog.message", selection.entry.shortHash),
            GitReflogBundle.message("reflog.branch.dialog.title"),
            null,
            "",
            GitNewBranchNameValidator.newInstance(repositories),
        ) ?: return

        GitBrancher.getInstance(selection.project)
            .checkoutNewBranchStartingFrom(name, selection.entry.hash, repositories, null)
    }
}
