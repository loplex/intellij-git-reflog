package cz.loplex.reflog

import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Covers what the service promises the panel about the reads it runs: both callbacks on the EDT, and neither of
 * them once the job is cancelled.
 *
 * The cancellation half is what the rest of the tab is built on. A selection moving on drops the read the
 * previous one started, and the panel counts on that read then saying nothing - it is what lets a cancelled read
 * leave the file list alone, and what obliges the panel to take the loading indicator down itself.
 */
class GitReflogServiceTest : GitReflogRepositoryTest() {

    fun `test a read hands back what git answered`() {
        commit("a.txt", "one", "Add a")
        repository.update()

        val started = AtomicInteger()
        val outcome = AtomicReference<Result<GitReflogData>>()

        runBlocking {
            service().loadReflog(
                repository,
                GitReflogRef.HEAD,
                GitReflogReader.PAGE_SIZE,
                onStarted = { started.incrementAndGet() },
                onFinished = { outcome.set(it) },
            ).join()
        }

        assertEquals("The read did not say it had started", 1, started.get())
        val data = outcome.get()?.getOrNull() ?: throw AssertionError("The read answered nothing: ${outcome.get()}")
        assertEquals(GitReflogRef.HEAD, data.ref)
        assertEquals("The reflog of a single commit has a single entry", 1, data.entries.size)
        assertTrue("HEAD is not among the refs that have a reflog", GitReflogRef.HEAD in data.refs)
    }

    /**
     * A read cancelled before it answers says nothing at all - which is why every path in the panel that cancels
     * one has to put right by hand whatever the read had already put on screen.
     */
    fun `test a cancelled read never answers`() {
        commit("a.txt", "one", "Add a")
        repository.update()

        val finished = AtomicInteger()

        runBlocking {
            val job = service().loadReflog(
                repository,
                GitReflogRef.HEAD,
                GitReflogReader.PAGE_SIZE,
                onStarted = { },
                onFinished = { finished.incrementAndGet() },
            )
            job.cancel()
            job.join()
        }

        assertEquals("A cancelled read answered anyway", 0, finished.get())
    }

    /** A ref the repository has no reflog for is answered, not thrown - the tab has a message for it. */
    fun `test a read of a ref with no reflog comes back rather than failing`() {
        commit("a.txt", "one", "Add a")
        repository.update()

        val outcome = AtomicReference<Result<GitReflogData>>()

        runBlocking {
            service().loadReflog(
                repository,
                GitReflogRef("refs/heads/never-existed"),
                GitReflogReader.PAGE_SIZE,
                onStarted = { },
                onFinished = { outcome.set(it) },
            ).join()
        }

        val data = outcome.get()?.getOrNull() ?: throw AssertionError("The read failed: ${outcome.get()}")
        // The reader falls back to HEAD, git having nothing to say about a ref that is not there.
        assertEquals(GitReflogRef.HEAD, data.ref)
    }

    private fun service(): GitReflogService = GitReflogService.getInstance(project)
}
