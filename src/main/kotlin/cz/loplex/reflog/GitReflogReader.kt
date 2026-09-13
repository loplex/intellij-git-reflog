package cz.loplex.reflog

import com.intellij.openapi.vcs.VcsException
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.io.File

/**
 * Reads what a repository holds in its reflogs: which refs have one, and the entries of one of them.
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

    private const val LOGS_DIRECTORY = "logs"
    private const val REFS_DIRECTORY = "refs"

    /**
     * Runs `git reflog` for [ref] and parses its output. Blocking - call from a background thread.
     *
     * A ref that exists but was never logged - a branch created while `core.logAllRefUpdates` was off - is not an
     * error for git either; it answers with an empty reflog.
     *
     * @throws VcsException when git fails
     */
    @Throws(VcsException::class)
    fun readReflog(repository: GitRepository, ref: GitReflogRef): List<GitReflogEntry> {
        // A repository without commits has no reflog at all, and asking git for one is an error, not an empty answer.
        if (repository.currentRevision == null) return emptyList()

        val handler = GitLineHandler(repository.project, repository.root, GitCommand.REF_LOG)
        handler.addParameters("show", "--date=unix", "--max-count=$MAX_ENTRIES", "--pretty=format:$PRETTY_FORMAT", ref.name)
        handler.setSilent(true)

        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) throw VcsException(result.errorOutputAsJoinedString)

        return result.output.mapIndexedNotNull { index, line -> parseEntry(index, line, ref) }
    }

    /**
     * Lists the refs this repository has a reflog for, by looking at the reflogs themselves.
     *
     * There is no git command that answers this on every supported version - `git reflog list` only arrived in
     * git 2.45 - and the set cannot be derived from the refs either: a fresh clone leaves `refs/remotes/origin/HEAD`
     * with a reflog and `refs/remotes/origin/master` without one, and `refs/stash` appears only once something is
     * stashed. The reflog files are the answer, so they are what gets read.
     *
     * Blocking - call from a background thread.
     *
     * @throws VcsException when git cannot say where the repository keeps its logs
     */
    @Throws(VcsException::class)
    fun listRefs(repository: GitRepository): List<GitReflogRef> {
        val (gitDirectory, commonDirectory) = logDirectories(repository)
        val refs = mutableListOf<GitReflogRef>()

        if (File(gitDirectory, "$LOGS_DIRECTORY/${GitReflogRef.HEAD.name}").isFile) refs.add(GitReflogRef.HEAD)

        val refLogs = File(commonDirectory, "$LOGS_DIRECTORY/$REFS_DIRECTORY")
        if (refLogs.isDirectory) {
            refLogs.walkTopDown()
                .filter { it.isFile }
                .mapTo(refs) { GitReflogRef("$REFS_DIRECTORY/${it.relativeTo(refLogs).invariantSeparatorsPath}") }
        }

        return refs.sortedWith(GitReflogRef.ORDER)
    }

    /**
     * The directory holding `logs/HEAD` and the one holding `logs/refs`.
     *
     * They are the same directory in an ordinary repository and differ inside a linked worktree, which keeps a
     * HEAD reflog of its own while sharing the ref reflogs with the repository it was created from.
     */
    @Throws(VcsException::class)
    private fun logDirectories(repository: GitRepository): Pair<File, File> {
        val handler = GitLineHandler(repository.project, repository.root, GitCommand.REV_PARSE)
        handler.addParameters("--git-dir", "--git-common-dir")
        handler.setSilent(true)

        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) throw VcsException(result.errorOutputAsJoinedString)

        // Both are answered relative to the repository root whenever git can put them that way.
        val root = File(repository.root.path)
        val paths = result.output.filter { it.isNotBlank() }.map { root.resolve(it) }
        val gitDirectory = paths.firstOrNull() ?: throw VcsException(GitReflogBundle.message("reflog.error.git.dir"))

        return gitDirectory to (paths.getOrNull(1) ?: gitDirectory)
    }

    private fun parseEntry(index: Int, line: String, ref: GitReflogRef): GitReflogEntry? {
        val fields = line.split(FIELD_SEPARATOR)
        if (fields.size < 3) return null
        val (hash, dateSelector, subject) = fields

        return GitReflogEntry(
            selector = dateSelector.substringBefore(SELECTOR_SEPARATOR) + SELECTOR_SEPARATOR + index + "}",
            hash = hash,
            timestamp = parseTimestamp(dateSelector),
            action = actionOf(subject, ref),
            description = descriptionOf(subject, ref),
        )
    }

    /**
     * Reflog subjects are written as "<action>: <details>", e.g. "checkout: moving from master to feature", and the
     * part before the colon is what the entry did.
     *
     * The stash is the exception: its subjects read "WIP on master: eddeef8 first", where the colon separates the
     * branch from the commit and nothing in the subject is an action. Every stash entry does the same thing, so
     * there is no action to show and none to filter by either.
     *
     * Entries written by older git versions or by scripts may carry no colon at all, and are taken as an action
     * with no details.
     */
    private fun actionOf(subject: String, ref: GitReflogRef): String =
        if (ref.kind == GitReflogRef.Kind.STASH) "" else subject.substringBefore(':').trim()

    private fun descriptionOf(subject: String, ref: GitReflogRef): String =
        if (ref.kind == GitReflogRef.Kind.STASH) subject.trim()
        else subject.substringAfter(':', missingDelimiterValue = "").trim()

    /** Extracts the seconds out of a `HEAD@{1789253693}` selector. */
    private fun parseTimestamp(dateSelector: String): Long {
        val seconds = dateSelector.substringAfter(SELECTOR_SEPARATOR, missingDelimiterValue = "").substringBefore('}')
        return (seconds.toLongOrNull() ?: 0L) * 1000L
    }
}
