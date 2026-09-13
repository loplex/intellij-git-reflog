package cz.loplex.reflog

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One reading of the reflogs of a repository: which refs have one, which of them is being shown, and its entries.
 */
internal data class GitReflogData(
    val refs: List<GitReflogRef>,
    val ref: GitReflogRef,
    val entries: List<GitReflogEntry>,
)

/**
 * Runs the git reads the tab needs off the EDT and hands their outcome back on it.
 */
@Service(Service.Level.PROJECT)
internal class GitReflogService(private val project: Project, private val coroutineScope: CoroutineScope) {

    /**
     * Reads the refs of [repository] that have a reflog, and the newest [limit] entries of [ref], in the
     * background.
     *
     * Both callbacks are invoked on the EDT: [onStarted] before git is asked anything, [onFinished] with either the
     * entries or the failure that git reported. Neither runs once the returned job is cancelled.
     */
    fun loadReflog(
        repository: GitRepository,
        ref: GitReflogRef,
        limit: Int,
        onStarted: () -> Unit,
        onFinished: (Result<GitReflogData>) -> Unit,
    ): Job = coroutineScope.launch {
        withContext(Dispatchers.EDT) { onStarted() }

        val result = withContext(Dispatchers.IO) {
            try {
                Result.success(read(repository, ref, limit))
            }
            catch (e: VcsException) {
                Result.failure(e)
            }
        }

        withContext(Dispatchers.EDT) { onFinished(result) }
    }

    /**
     * Reads the changes [preferred] asks about for [selection], in the background.
     *
     * Shaped like [loadReflog] - both callbacks on the EDT, neither of them run once the returned job is
     * cancelled - because the panel treats the two reads the same way: a new selection drops the read the
     * previous one started.
     *
     * The mode actually read is settled here rather than by the caller, because settling it can need git: see
     * [GitReflogChangesResult.mode].
     */
    fun loadChanges(
        repository: GitRepository,
        selection: GitReflogSelection,
        preferred: GitReflogDiffMode,
        onStarted: () -> Unit,
        onFinished: (Result<GitReflogChangesResult>) -> Unit,
    ): Job = coroutineScope.launch {
        withContext(Dispatchers.EDT) { onStarted() }

        val result = withContext(Dispatchers.IO) {
            try {
                Result.success(readChangesFor(project, repository, selection, preferred))
            }
            catch (e: VcsException) {
                Result.failure(e)
            }
        }

        withContext(Dispatchers.EDT) { onFinished(result) }
    }

    private fun read(repository: GitRepository, ref: GitReflogRef, limit: Int): GitReflogData {
        val refs = GitReflogReader.listRefs(repository)
        // The ref asked for can be gone by now - a branch deleted, a stash dropped - and git answers a reflog
        // request for a ref that no longer exists with a fatal error. HEAD is the one that is always there.
        val shown = if (ref in refs) ref else GitReflogRef.HEAD

        return GitReflogData(refs, shown, GitReflogReader.readReflog(repository, shown, limit))
    }

    companion object {
        fun getInstance(project: Project): GitReflogService = project.service()
    }
}
