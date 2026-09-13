package cz.loplex.reflog

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * Reads what the commit behind [entry] changed against its parent. Blocking - call from a background thread.
 *
 * The changes are read straight from `git show` rather than looked up in the Log, which is what makes them
 * available for a commit no ref points to any more - the resets, amends and rebases that are the very reason to
 * open the reflog.
 */
internal fun readReflogEntryChanges(
    project: Project,
    repository: GitRepository,
    entry: GitReflogEntry,
): List<Change> =
    // skipDiffsForMerge=true keeps a merge commit comparable against its first parent instead of failing.
    GitChangeUtils
        .getRevisionChanges(project, repository.root, entry.hash, true, false, false)
        .changes
        .toList()

/**
 * Reads the changes [mode] asks about for [selection]. Blocking - call from a background thread.
 *
 * The mode is taken as already fitting the selection: what to do about one that does not is a question for the
 * panel, which has [GitReflogDiffMode.effectiveFor] for it.
 *
 * @throws VcsException when git fails
 */
@Throws(VcsException::class)
internal fun readReflogChanges(
    project: Project,
    repository: GitRepository,
    selection: GitReflogSelection,
    mode: GitReflogDiffMode,
): List<Change> = when (mode) {
    GitReflogDiffMode.REFLOG_STEP, GitReflogDiffMode.BETWEEN_SELECTED ->
        readDiff(project, repository, mode.oldSideOf(selection), mode.newSideOf(selection))
    GitReflogDiffMode.UNION -> readUnion(project, repository, selection)
    GitReflogDiffMode.WORKING_TREE -> readWorkingTreeDiff(project, repository, mode.oldSideOf(selection))
}

/**
 * Compares the two states the reflog recorded, as `git diff <old>..<new>` does.
 *
 * Two dots rather than three: what is wanted is how the working tree differed, not what one branch added over
 * another, and the two part ways for exactly the entries the reflog exists to show - the resets and the
 * checkouts that move a ref sideways rather than forwards.
 */
private fun readDiff(
    project: Project,
    repository: GitRepository,
    old: GitReflogEntry?,
    new: GitReflogEntry?,
): List<Change> {
    if (old == null || new == null) return emptyList()
    return GitChangeUtils.getDiff(project, repository.root, old.hash, new.hash, null).toList()
}

/**
 * Merges what each selected commit changed against its own parent into a single tree, the way the Log answers a
 * multiple selection.
 *
 * The entries are read oldest first so that zipping them leaves each file with the state it started in and the
 * state it ended in, rather than the other way round.
 */
private fun readUnion(
    project: Project,
    repository: GitRepository,
    selection: GitReflogSelection,
): List<Change> {
    val changes = selection.chronological.flatMap { readReflogEntryChanges(project, repository, it) }
    return CommittedChangesTreeBrowser.zipChanges(changes)
}

/** Compares the recorded state against the working tree as it stands now, as `git diff <revision>` does. */
private fun readWorkingTreeDiff(
    project: Project,
    repository: GitRepository,
    entry: GitReflogEntry?,
): List<Change> {
    if (entry == null) return emptyList()
    return GitChangeUtils
        .getDiffWithWorkingDir(project, repository.root, entry.hash, null, false, true)
        .toList()
}

/**
 * Answers whether the selected entries sit on one line of history, which is what makes merging their individual
 * diffs meaningful. Blocking - call from a background thread.
 *
 * Asked of neighbouring pairs rather than of the two ends alone: a selection can begin and end on one branch and
 * still step onto another in between, and merging the diffs of commits that undo one another is the reading that
 * [GitReflogDiffMode.UNION] must not offer.
 *
 * A single entry is linear by definition, and never costs a git call.
 */
internal fun readAncestry(repository: GitRepository, selection: GitReflogSelection): GitReflogAncestry {
    val chronological = selection.chronological
    if (chronological.size < 2) return GitReflogAncestry.LINEAR

    val linear = chronological.zipWithNext().all { (older, newer) -> isAncestor(repository, older, newer) }
    return if (linear) GitReflogAncestry.LINEAR else GitReflogAncestry.DIVERGED
}

/**
 * Whether [older] is reachable from [newer], as `git merge-base --is-ancestor` decides it.
 *
 * The command answers by exit code - 0 for yes, 1 for no - so a failure is an answer rather than an error, and
 * anything git could not decide is taken as "no": a mode is better left unoffered than offered wrongly.
 */
private fun isAncestor(repository: GitRepository, older: GitReflogEntry, newer: GitReflogEntry): Boolean {
    if (older.hash == newer.hash) return true

    val handler = GitLineHandler(repository.project, repository.root, GitCommand.MERGE_BASE)
    handler.addParameters("--is-ancestor", older.hash, newer.hash)
    handler.setSilent(true)

    return Git.getInstance().runCommand(handler).success()
}
