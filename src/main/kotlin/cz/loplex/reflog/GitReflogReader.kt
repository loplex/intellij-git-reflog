package cz.loplex.reflog

import com.intellij.openapi.vcs.VcsException
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * Reads the reflog of a ref of a given repository.
 */
internal object GitReflogReader {
    /**
     * Machine-readable replacement for the default `git reflog` output: every placeholder expands to a single
     * line-free token, so one output line is exactly one reflog record.
     *
     * `%gd` is the only placeholder that carries the time of the reflog record itself (the commit date of `%ct`
     * can be days older, for example after a checkout). Together with `--date=unix` it expands to
     * `HEAD@{1789253693}`. This is also why the index form of the selector cannot be read from git here:
     * `%gD`/`%gd` render *either* the index *or* the date, never both. The index is derived from the record
     * position instead, which holds because the whole reflog is read starting from its newest entry.
     */
    private const val PRETTY_FORMAT = "%H%x01%gd%x01%gs"
    private const val FIELD_SEPARATOR = '\u0001'
    private const val SELECTOR_SEPARATOR = "@{"

    /** Upper bound for a single read; reflogs of long-lived repositories can hold tens of thousands of records. */
    const val MAX_ENTRIES = 1000

    /** The ref every repository has a reflog for, and the one the tab starts on. */
    const val HEAD_REF = "HEAD"

    /**
     * Runs `git reflog` for [ref] and parses its output. Blocking - call from a background thread.
     *
     * A ref that exists but was never logged - a branch created while `core.logAllRefUpdates` was off - is not an
     * error for git either; it answers with an empty reflog.
     *
     * @throws VcsException when git fails
     */
    @Throws(VcsException::class)
    fun readReflog(repository: GitRepository, ref: String): List<GitReflogEntry> {
        // A repository without commits has no reflog at all, and asking git for one is an error, not an empty answer.
        if (repository.currentRevision == null) return emptyList()

        val handler = GitLineHandler(repository.project, repository.root, GitCommand.REF_LOG)
        handler.addParameters("show", "--date=unix", "--max-count=$MAX_ENTRIES", "--pretty=format:$PRETTY_FORMAT", ref)
        handler.setSilent(true)

        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) throw VcsException(result.errorOutputAsJoinedString)

        return result.output.mapIndexedNotNull { index, line -> parseEntry(index, line) }
    }

    private fun parseEntry(index: Int, line: String): GitReflogEntry? {
        val fields = line.split(FIELD_SEPARATOR)
        if (fields.size < 3) return null
        val (hash, dateSelector, subject) = fields

        // Reflog subjects are written as "<action>: <details>", e.g. "checkout: moving from master to feature".
        // Entries written by older git versions or by scripts may carry no details at all.
        val action = subject.substringBefore(':').trim()
        val description = subject.substringAfter(':', missingDelimiterValue = "").trim()

        return GitReflogEntry(
            selector = dateSelector.substringBefore(SELECTOR_SEPARATOR) + SELECTOR_SEPARATOR + index + "}",
            hash = hash,
            timestamp = parseTimestamp(dateSelector),
            action = action,
            description = description,
        )
    }

    /** Extracts the seconds out of a `HEAD@{1789253693}` selector. */
    private fun parseTimestamp(dateSelector: String): Long {
        val seconds = dateSelector.substringAfter(SELECTOR_SEPARATOR, missingDelimiterValue = "").substringBefore('}')
        return (seconds.toLongOrNull() ?: 0L) * 1000L
    }
}
