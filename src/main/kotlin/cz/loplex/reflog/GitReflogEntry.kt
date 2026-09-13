package cz.loplex.reflog

import com.intellij.openapi.util.NlsSafe
import git4idea.GitRevisionNumber
import java.util.Date

/**
 * A single record of `git reflog`: one movement of a ref, as stored in `.git/logs`.
 */
internal data class GitReflogEntry(
    /** Selector of the entry in its index form, for example `HEAD@{3}`. */
    val selector: @NlsSafe String,
    /** Full hash of the commit the ref pointed to after the movement. */
    val hash: @NlsSafe String,
    /** Time of the reflog record itself - not of the commit it points to - in milliseconds. */
    val timestamp: Long,
    /** Operation that moved the ref: `commit`, `checkout`, `reset`, `rebase (finish)`, ... */
    val action: @NlsSafe String,
    /** Reflog message without the [action] prefix, for example `moving from master to feature`. */
    val description: @NlsSafe String,
    /** Author of the commit [hash] - not of the reflog record, which git does not attribute to anyone. */
    val author: @NlsSafe String,
    /** First line of the message of the commit [hash]. */
    val subject: @NlsSafe String,
) {
    val shortHash: @NlsSafe String get() = hash.take(SHORT_HASH_LENGTH)

    /**
     * First word of [action], which is what a reader thinks of as the kind of the operation: `merge feature` and
     * `commit (amend)` belong with the other merges and commits rather than forming kinds of their own.
     */
    val actionKind: @NlsSafe String get() = action.substringBefore(' ')

    val revisionNumber: GitRevisionNumber get() = GitRevisionNumber(hash, Date(timestamp))

    /**
     * What identifies the record across a re-read, so that a reload can put the selection back.
     *
     * Everything but the [selector], because the selector is a position rather than an identity: every new record
     * pushes `HEAD@{0}` down to `HEAD@{1}`, and keeping the selector would quietly move the selection to the
     * neighbouring entry after every commit.
     */
    val identity: Any get() = copy(selector = "")

    private companion object {
        const val SHORT_HASH_LENGTH = 8
    }
}
