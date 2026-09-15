package cz.loplex.reflog.actions

import git4idea.branch.GitBrancher

/**
 * Checks the working tree out at the commit of the selected entry, leaving the repository on a detached HEAD.
 *
 * This is the way back to a commit no ref points at any more, and the one operation that cannot be reached from
 * the Log for exactly those commits.
 */
internal class GitReflogCheckoutRevisionAction : GitReflogEntryAction() {

    override fun perform(selection: GitReflogEntryTarget) {
        // detach=false matches what the Log tab passes: the argument is a hash rather than a branch, so git
        // detaches on its own and GitBrancher keeps its own handling of local changes and of the notification.
        GitBrancher.getInstance(selection.project)
            .checkout(selection.entry.hash, false, listOf(selection.repository), null)
    }
}
