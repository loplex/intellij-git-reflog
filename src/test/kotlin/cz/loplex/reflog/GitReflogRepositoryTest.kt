package cz.loplex.reflog

import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.vcs.test.VcsPlatformTest
import git4idea.GitVcs
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * A test with a git repository of its own, built for the occasion and registered with git4idea.
 *
 * What the tab answers is in the end whatever git answers, so the questions worth asking of it are asked of a
 * real repository rather than of entries made up in a test - the rules for what to ask are covered without one.
 */
abstract class GitReflogRepositoryTest : VcsPlatformTest() {

    protected lateinit var repository: GitRepository

    /** Git reads belong off the EDT, here as in the tab. */
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()

        // The project directory is the repository root, and it is not on disk until something puts it there.
        Files.createDirectories(projectNioRoot)

        git("init", "--quiet")
        git("config", "user.name", "Test")
        git("config", "user.email", "test@example.com")
        git("config", "commit.gpgsign", "false")

        // The .git directory was written behind the VFS's back, and the mapping is only believed once it is seen.
        VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)

        vcsManager.setDirectoryMappings(listOf(VcsDirectoryMapping(projectPath, GitVcs.NAME)))
        vcsManager.waitForInitialized()

        val repositories = GitRepositoryManager.getInstance(project)
        repository = repositories.getRepositoryForRootQuick(projectRoot)
            ?: repositories.getRepositoryForRoot(projectRoot)
            ?: throw AssertionError("The test repository did not register with git4idea")
    }

    /**
     * The reflog of HEAD as git has it, newest first.
     *
     * The repository state is re-read first: the commits here are made by running git rather than through
     * git4idea, so nothing has told it that the repository has a HEAD now - and the reader answers a repository
     * with no current revision with an empty reflog, git having no reflog to give for one.
     */
    // Internal rather than protected: the entries it returns are internal, and a protected member may not expose
    // one. Subclasses are in this module either way, so they see it just the same.
    internal fun reflog(): List<GitReflogEntry> {
        repository.update()
        return GitReflogReader.readReflog(repository, GitReflogRef.HEAD, GitReflogReader.PAGE_SIZE)
    }

    protected fun commit(name: String, content: String, message: String) {
        File(projectNioRoot.toFile(), name).writeText(content)
        git("add", name)
        git("commit", "-m", message, "--quiet")
    }

    protected fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(projectNioRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("git ${args.joinToString(" ")} did not finish", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals("git ${args.joinToString(" ")} failed: $output", 0, process.exitValue())
    }
}
