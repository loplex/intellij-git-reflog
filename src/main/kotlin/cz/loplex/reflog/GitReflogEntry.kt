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
) {
    val shortHash: @NlsSafe String get() = hash.take(SHORT_HASH_LENGTH)

    val revisionNumber: GitRevisionNumber get() = GitRevisionNumber(hash, Date(timestamp))

    private companion object {
        const val SHORT_HASH_LENGTH = 8
    }
}
