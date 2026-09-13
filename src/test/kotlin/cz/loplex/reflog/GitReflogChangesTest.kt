package cz.loplex.reflog

import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.vcs.test.VcsPlatformTest
import git4idea.GitVcs
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Covers what the four comparisons actually read out of git, against a repository built for the occasion.
 *
 * The rules deciding which comparison fits a selection are covered without a repository in
 * [GitReflogDiffModeTest]; this is the other half - that the comparison chosen is handed the revisions it means
 * to be handed, which no amount of reasoning about the rules can show.
 */
class GitReflogChangesTest : VcsPlatformTest() {

    private lateinit var repository: GitRepository

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

    fun `test a step reads what the movement did, not what the commit did`() {
        commit("a.txt", "one", "Add a")
        commit("b.txt", "two", "Add b")
        git("reset", "--hard", "HEAD~1", "--quiet")

        // HEAD@{0} is the reset, which moved HEAD back onto the commit that added a.txt alone.
        val changes = read(GitReflogDiffMode.REFLOG_STEP, selectionOf(0))

        // The commit HEAD now stands on added a.txt, but the movement onto it *removed* b.txt.
        assertEquals(setOf("b.txt"), namesOf(changes))
        assertNull("b.txt was added by the step, not removed by it", changes.single().afterRevision)
    }

    fun `test a step over several entries spans all of them`() {
        commit("a.txt", "one", "Add a")
        commit("b.txt", "two", "Add b")
        commit("c.txt", "three", "Add c")

        // The two newest entries together: the commits that added c.txt and b.txt.
        assertEquals(setOf("b.txt", "c.txt"), namesOf(read(GitReflogDiffMode.REFLOG_STEP, selectionOf(0, 1))))
    }

    fun `test comparing selected states leaves out the movement onto the oldest of them`() {
        commit("a.txt", "one", "Add a")
        commit("b.txt", "two", "Add b")
        commit("c.txt", "three", "Add c")

        // From the state after b.txt was added to the state after c.txt was: c.txt alone, b.txt already there.
        assertEquals(setOf("c.txt"), namesOf(read(GitReflogDiffMode.BETWEEN_SELECTED, selectionOf(0, 1))))
    }

    fun `test merging the commits of several entries gathers every file each of them touched`() {
        commit("a.txt", "one", "Add a")
        commit("b.txt", "two", "Add b")
        commit("a.txt", "one again", "Change a")

        // Two commits touching a.txt and one touching b.txt, merged into one entry per file.
        val changes = read(GitReflogDiffMode.UNION, selectionOf(0, 1, 2))
        assertEquals(setOf("a.txt", "b.txt"), namesOf(changes))
        assertEquals("a.txt is listed once, not once per commit", 2, changes.size)
    }

    fun `test the working tree is read as it stands, uncommitted`() {
        commit("a.txt", "one", "Add a")
        File(projectNioRoot.toFile(), "a.txt").writeText("changed on disk")

        assertEquals(setOf("a.txt"), namesOf(read(GitReflogDiffMode.WORKING_TREE, selectionOf(0))))
    }

    fun `test entries on one line of history are linear`() {
        commit("a.txt", "one", "Add a")
        commit("b.txt", "two", "Add b")

        assertEquals(GitReflogAncestry.LINEAR, readAncestry(repository, selectionOf(0, 1)))
    }

    fun `test entries on branches that never met are diverged`() {
        commit("a.txt", "one", "Add a")
        git("checkout", "-b", "feature", "--quiet")
        commit("b.txt", "two", "Add b on feature")
        git("checkout", "master", "--quiet")
        commit("c.txt", "three", "Add c on master")

        // The newest entry stands on master, and the commit made on feature is not reachable from it.
        val entries = reflog()
        val feature = entries.first { it.subject == "Add b on feature" }
        val master = entries.first { it.subject == "Add c on master" }

        assertEquals(
            GitReflogAncestry.DIVERGED,
            readAncestry(repository, GitReflogSelection(GitReflogRef.HEAD, entries, listOf(master, feature))),
        )
    }

    fun `test what is on offer is settled even when the chosen mode is not the one it bears on`() {
        commit("a.txt", "one", "Add a")
        git("checkout", "-q", "-b", "feature")
        commit("b.txt", "two", "Add b on feature")
        git("checkout", "-q", "master")
        commit("c.txt", "three", "Add c on master")

        val entries = reflog()
        val diverged = GitReflogSelection(
            GitReflogRef.HEAD,
            entries,
            listOf(entries.first { it.subject == "Add c on master" }, entries.first { it.subject == "Add b on feature" }),
        )

        // Reflog Step fits this selection, so nothing forces the graph to be walked for the reading's own sake -
        // but the switch still has to know that merging the two is not on offer, so it is walked anyway.
        val outcome = readChangesFor(project, repository, diverged, GitReflogDiffMode.REFLOG_STEP)

        assertEquals(GitReflogDiffMode.REFLOG_STEP, outcome.mode)
        assertEquals(GitReflogAncestry.DIVERGED, outcome.ancestry)
        assertFalse(
            "Merging diverged entries was left on offer",
            GitReflogDiffMode.UNION.isApplicableTo(diverged, outcome.ancestry),
        )
    }

    fun `test a single entry is linear without asking git`() {
        commit("a.txt", "one", "Add a")

        assertEquals(GitReflogAncestry.LINEAR, readAncestry(repository, selectionOf(0)))
    }

    private fun read(mode: GitReflogDiffMode, selection: GitReflogSelection): List<Change> =
        readReflogChanges(project, repository, selection, mode)

    private fun namesOf(changes: List<Change>): Set<String> =
        changes.mapTo(HashSet()) { ChangesUtil.getFilePath(it).name }

    /**
     * The reflog of HEAD as git has it, newest first.
     *
     * The repository state is re-read first: the commits here are made by running git rather than through
     * git4idea, so nothing has told it that the repository has a HEAD now - and the reader answers a repository
     * with no current revision with an empty reflog, git having no reflog to give for one.
     */
    private fun reflog(): List<GitReflogEntry> {
        repository.update()
        return GitReflogReader.readReflog(repository, GitReflogRef.HEAD, GitReflogReader.PAGE_SIZE)
    }

    /** A selection of the entries at [positions] in the reflog, newest being 0. */
    private fun selectionOf(vararg positions: Int): GitReflogSelection {
        val entries = reflog()
        return GitReflogSelection(GitReflogRef.HEAD, entries, positions.map { entries[it] })
    }

    private fun commit(name: String, content: String, message: String) {
        File(projectNioRoot.toFile(), name).writeText(content)
        git("add", name)
        git("commit", "-m", message, "--quiet")
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(projectNioRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("git ${args.joinToString(" ")} did not finish", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals("git ${args.joinToString(" ")} failed: $output", 0, process.exitValue())
    }
}
