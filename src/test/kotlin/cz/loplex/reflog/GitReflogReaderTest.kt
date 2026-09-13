package cz.loplex.reflog

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Covers how the reader decides which refs have a reflog. It reads the reflog files rather than asking git, so
 * the test builds the directories git would have written and checks what comes back out of them.
 */
class GitReflogReaderTest {

    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `a repository without any log directory has no refs`() {
        val gitDirectory = folder.newFolder(".git")

        assertEquals(emptyList<GitReflogRef>(), GitReflogReader.refsIn(gitDirectory, gitDirectory))
    }

    @Test
    fun `HEAD is offered when its reflog file exists`() {
        val gitDirectory = gitDirectoryWith("logs/HEAD")

        assertEquals(listOf(GitReflogRef.HEAD), GitReflogReader.refsIn(gitDirectory, gitDirectory))
    }

    @Test
    fun `refs are named after their path below logs`() {
        val gitDirectory = gitDirectoryWith(
            "logs/refs/heads/master",
            "logs/refs/heads/feature/parser",
            "logs/refs/remotes/origin/HEAD",
            "logs/refs/stash",
            "logs/refs/notes/commits",
        )

        assertEquals(
            listOf(
                "refs/heads/feature/parser",
                "refs/heads/master",
                "refs/remotes/origin/HEAD",
                "refs/stash",
                "refs/notes/commits",
            ),
            GitReflogReader.refsIn(gitDirectory, gitDirectory).map { it.name },
        )
    }

    @Test
    fun `refs come back in the order the selector offers them`() {
        val gitDirectory = gitDirectoryWith(
            "logs/refs/notes/commits",
            "logs/refs/stash",
            "logs/refs/remotes/origin/main",
            "logs/refs/heads/master",
            "logs/HEAD",
        )

        assertEquals(
            listOf(
                GitReflogRef.Kind.HEAD,
                GitReflogRef.Kind.LOCAL_BRANCH,
                GitReflogRef.Kind.REMOTE_BRANCH,
                GitReflogRef.Kind.STASH,
                GitReflogRef.Kind.OTHER,
            ),
            GitReflogReader.refsIn(gitDirectory, gitDirectory).map { it.kind },
        )
    }

    @Test
    fun `a worktree takes HEAD from its own directory and the refs from the common one`() {
        // git gives a linked worktree a HEAD reflog of its own while the ref reflogs stay with the repository
        // the worktree was created from, which is why the two directories are asked for separately.
        val commonDirectory = gitDirectoryWith("logs/HEAD", "logs/refs/heads/master")
        val worktreeDirectory = folder.newFolder("worktree").also { File(it, "logs").mkdirs() }
        File(worktreeDirectory, "logs/HEAD").writeText("")

        assertEquals(
            listOf("HEAD", "refs/heads/master"),
            GitReflogReader.refsIn(worktreeDirectory, commonDirectory).map { it.name },
        )
    }

    @Test
    fun `a worktree without a HEAD reflog of its own does not borrow the common one`() {
        val commonDirectory = gitDirectoryWith("logs/HEAD", "logs/refs/heads/master")
        val worktreeDirectory = folder.newFolder("worktree")

        assertEquals(
            listOf("refs/heads/master"),
            GitReflogReader.refsIn(worktreeDirectory, commonDirectory).map { it.name },
        )
    }

    /** Builds a git directory holding an empty reflog file at each of [reflogs]. */
    private fun gitDirectoryWith(vararg reflogs: String): File {
        val gitDirectory = folder.newFolder(".git-${counter++}")
        reflogs.forEach { path ->
            val file = File(gitDirectory, path)
            file.parentFile.mkdirs()
            file.writeText("")
        }
        return gitDirectory
    }

    private var counter = 0
}
