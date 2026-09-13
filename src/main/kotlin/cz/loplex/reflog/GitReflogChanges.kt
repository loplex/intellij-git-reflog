package cz.loplex.reflog

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import git4idea.changes.GitChangeUtils
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
