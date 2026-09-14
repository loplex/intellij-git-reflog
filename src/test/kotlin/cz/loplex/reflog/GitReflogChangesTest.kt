package cz.loplex.reflog

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import java.io.File

/**
 * Covers what the four comparisons actually read out of git, against a repository built for the occasion.
 *
 * The rules deciding which comparison fits a selection are covered without a repository in
 * [GitReflogDiffModeTest]; this is the other half - that the comparison chosen is handed the revisions it means
 * to be handed, which no amount of reasoning about the rules can show.
 */
class GitReflogChangesTest : GitReflogRepositoryTest() {

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

    /**
     * The oldest entry of the reflog records a movement whose starting state the reflog no longer holds, so the
     * reading that spans it has nothing to read - and the one that steps in reads a commit with no parent, which
     * is the case the reflog's own oldest entry always is.
     */
    fun `test the oldest entry is read by the reading that needs no state before it`() {
        commit("a.txt", "one", "Add a")
        commit("b.txt", "two", "Add b")

        val entries = reflog()
        val oldest = GitReflogSelection(GitReflogRef.HEAD, entries, listOf(entries.last()))

        assertNull("The reflog reaches past its own oldest entry", oldest.beforeOldest)

        val outcome = readChangesFor(project, repository, oldest, GitReflogDiffMode.REFLOG_STEP)

        // Reflog Step steps aside for the reading that compares the commit with its own parent - of which the
        // initial commit has none, so what it changed is everything it introduced.
        assertEquals(GitReflogDiffMode.UNION, outcome.mode)
        assertEquals(setOf("a.txt"), namesOf(outcome.changes))

        // What was picked stays picked, so it comes back on the next selection that suits it.
        val modes = GitReflogDiffModes.of(GitReflogDiffMode.REFLOG_STEP, oldest, outcome.ancestry)
        assertEquals(GitReflogDiffMode.REFLOG_STEP, modes.preferred)
        assertEquals(GitReflogDiffMode.UNION, modes.effective)
        assertFalse("Reflog Step was left on offer for the oldest entry", GitReflogDiffMode.REFLOG_STEP in modes.applicable)
    }

    /**
     * A stash reflog is a stack of unrelated entries rather than a timeline of one state: nothing in it moved
     * anything onto anything else, so several of them selected leave every reading without an answer.
     *
     * The two readings that treat a reflog as a timeline are ruled out by the ref alone; the one that merges
     * commits is ruled out by the graph, and this is what says the graph really answers that way for stashes.
     */
    fun `test several stash entries leave every reading without an answer`() {
        commit("a.txt", "one", "Add a")

        File(projectNioRoot.toFile(), "a.txt").writeText("changed once")
        git("stash", "push", "--quiet", "-m", "first")
        File(projectNioRoot.toFile(), "a.txt").writeText("changed twice")
        git("stash", "push", "--quiet", "-m", "second")

        repository.update()
        val entries = GitReflogReader.readReflog(repository, STASH, GitReflogReader.PAGE_SIZE)
        assertEquals("The two stashes did not reach the reflog: $entries", 2, entries.size)

        val selection = GitReflogSelection(STASH, entries, entries)
        // Neither stash was made on top of the other, so merging their commits is not on offer either.
        assertEquals(GitReflogAncestry.DIVERGED, readAncestry(repository, selection))

        val modes = GitReflogDiffModes.of(GitReflogDiffMode.REFLOG_STEP, selection, readAncestry(repository, selection))
        assertTrue("A stash pair has a reading after all: ${modes.applicable}", modes.applicable.isEmpty())
        assertNull("A stash pair has a reading on screen", modes.effective)
    }

    fun `test a single entry is linear without asking git`() {
        commit("a.txt", "one", "Add a")

        assertEquals(GitReflogAncestry.LINEAR, readAncestry(repository, selectionOf(0)))
    }

    private companion object {
        val STASH = GitReflogRef("refs/stash")
    }

    private fun read(mode: GitReflogDiffMode, selection: GitReflogSelection): List<Change> =
        readReflogChanges(project, repository, selection, mode)

    private fun namesOf(changes: List<Change>): Set<String> =
        changes.mapTo(HashSet()) { ChangesUtil.getFilePath(it).name }

    /** A selection of the entries at [positions] in the reflog, newest being 0. */
    private fun selectionOf(vararg positions: Int): GitReflogSelection {
        val entries = reflog()
        return GitReflogSelection(GitReflogRef.HEAD, entries, positions.map { entries[it] })
    }

}
