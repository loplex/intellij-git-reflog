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
 * Runs reflog reads off the EDT and hands the outcome back on it.
 */
@Service(Service.Level.PROJECT)
internal class GitReflogService(private val coroutineScope: CoroutineScope) {

    /**
     * Reads the reflog of [repository] in the background.
     *
     * Both callbacks are invoked on the EDT: [onStarted] before git is asked anything, [onFinished] with either the
     * entries or the failure that git reported. Neither runs once the returned job is cancelled.
     */
    fun loadHeadReflog(
        repository: GitRepository,
        onStarted: () -> Unit,
        onFinished: (Result<List<GitReflogEntry>>) -> Unit,
    ): Job = coroutineScope.launch {
        withContext(Dispatchers.EDT) { onStarted() }

        val result = withContext(Dispatchers.IO) {
            try {
                Result.success(GitReflogReader.readHeadReflog(repository))
            }
            catch (e: VcsException) {
                Result.failure(e)
            }
        }

        withContext(Dispatchers.EDT) { onFinished(result) }
    }

    companion object {
        fun getInstance(project: Project): GitReflogService = project.service()
    }
}
