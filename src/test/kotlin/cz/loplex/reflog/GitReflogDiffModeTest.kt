package cz.loplex.reflog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitReflogDiffModeTest {

    @Test
    fun `the sides of a step are the state before the oldest and the state after the newest`() {
        val selection = selection(commit, reset)

        assertEquals(checkout, GitReflogDiffMode.REFLOG_STEP.oldSideOf(selection))
        assertEquals(commit, GitReflogDiffMode.REFLOG_STEP.newSideOf(selection))
    }

    @Test
    fun `the sides of a comparison between selected states are the selected states themselves`() {
        val selection = selection(commit, reset)

        assertEquals(reset, GitReflogDiffMode.BETWEEN_SELECTED.oldSideOf(selection))
        assertEquals(commit, GitReflogDiffMode.BETWEEN_SELECTED.newSideOf(selection))
    }

    @Test
    fun `the working tree is compared against the newest selected state`() {
        val selection = selection(commit, reset)

        assertEquals(commit, GitReflogDiffMode.WORKING_TREE.oldSideOf(selection))
        // The other side is the working tree, which is no entry of the reflog.
        assertNull(GitReflogDiffMode.WORKING_TREE.newSideOf(selection))
    }

    @Test
    fun `a step needs an entry before the oldest selected one`() {
        assertTrue(applies(GitReflogDiffMode.REFLOG_STEP, selection(commit)))
        // The oldest entry of the reflog records a movement whose starting state the reflog no longer holds.
        assertFalse(applies(GitReflogDiffMode.REFLOG_STEP, selection(oldest)))
    }

    @Test
    fun `comparing selected states needs two of them`() {
        assertFalse(applies(GitReflogDiffMode.BETWEEN_SELECTED, selection(commit)))
        assertTrue(applies(GitReflogDiffMode.BETWEEN_SELECTED, selection(commit, reset)))
    }

    @Test
    fun `the working tree is compared against one selected state only`() {
        assertTrue(applies(GitReflogDiffMode.WORKING_TREE, selection(commit)))
        assertFalse(applies(GitReflogDiffMode.WORKING_TREE, selection(commit, reset)))
    }

    @Test
    fun `a stash reflog is no timeline, so the modes that read it as one do not apply`() {
        val selection = GitReflogSelection(STASH, entries, listOf(commit, reset))

        assertFalse(applies(GitReflogDiffMode.REFLOG_STEP, selection))
        assertFalse(applies(GitReflogDiffMode.BETWEEN_SELECTED, selection))
    }

    @Test
    fun `merging the changes of diverged commits is not offered`() {
        val selection = selection(commit, reset)

        assertTrue(GitReflogDiffMode.UNION.isApplicableTo(selection, GitReflogAncestry.LINEAR))
        assertFalse(GitReflogDiffMode.UNION.isApplicableTo(selection, GitReflogAncestry.DIVERGED))
    }

    @Test
    fun `a single commit against its parent needs nothing known about the graph`() {
        // Which is why walking it is left until a selection is made that the answer could bear on.
        assertTrue(GitReflogDiffMode.UNION.isApplicableTo(selection(commit), GitReflogAncestry.DIVERGED))
    }

    @Test
    fun `a mode that fits the selection is the one shown`() {
        val selection = selection(commit, reset)

        assertEquals(
            GitReflogDiffMode.BETWEEN_SELECTED,
            GitReflogDiffMode.BETWEEN_SELECTED.effectiveFor(selection, GitReflogAncestry.UNKNOWN),
        )
    }

    @Test
    fun `a mode that does not fit gives way to the nearest one that does`() {
        // Nothing can be said about the working tree of more than one state at once.
        assertEquals(
            GitReflogDiffMode.BETWEEN_SELECTED,
            GitReflogDiffMode.WORKING_TREE.effectiveFor(selection(commit, reset), GitReflogAncestry.UNKNOWN),
        )
        // And a single entry leaves nothing for a comparison between selected states to compare.
        assertEquals(
            GitReflogDiffMode.REFLOG_STEP,
            GitReflogDiffMode.BETWEEN_SELECTED.effectiveFor(selection(commit), GitReflogAncestry.UNKNOWN),
        )
    }

    @Test
    fun `the oldest entry of the reflog falls back to the commit against its parent`() {
        assertEquals(
            GitReflogDiffMode.UNION,
            GitReflogDiffMode.REFLOG_STEP.effectiveFor(selection(oldest), GitReflogAncestry.UNKNOWN),
        )
    }

    /**
     * Several stash entries are merged rather than read as a timeline: the readings that ask what a movement did
     * step aside for the one that asks what the entries hold.
     *
     * Their not being ancestors of one another does not rule that out, which it would on a timeline - it is true
     * of every pair of stash entries, so it says nothing about any particular pair.
     */
    @Test
    fun `several entries of a stash reflog are merged rather than read as a step`() {
        val selection = GitReflogSelection(STASH, entries, listOf(commit, reset))

        assertEquals(
            GitReflogDiffMode.UNION,
            GitReflogDiffMode.REFLOG_STEP.effectiveFor(selection, GitReflogAncestry.DIVERGED),
        )
        assertTrue(applies(GitReflogDiffMode.UNION, selection))
    }

    /** With nothing selected there is nothing to compare, which is the one case that leaves no reading at all. */
    @Test
    fun `an empty selection leaves nothing to show`() {
        assertNull(GitReflogDiffMode.REFLOG_STEP.effectiveFor(selection(), GitReflogAncestry.LINEAR))
    }

    @Test
    fun `nothing selected leaves nothing to show`() {
        assertNull(GitReflogDiffMode.REFLOG_STEP.effectiveFor(selection(), GitReflogAncestry.LINEAR))
    }

    @Test
    fun `the entries are ordered by the reflog, not by the order they were selected in`() {
        val selection = selection(reset, commit)

        assertEquals(commit, selection.newest)
        assertEquals(reset, selection.oldest)
        assertEquals(listOf(reset, commit), selection.chronological)
    }

    private fun applies(mode: GitReflogDiffMode, selection: GitReflogSelection) =
        mode.isApplicableTo(selection, GitReflogAncestry.UNKNOWN)

    private fun selection(vararg selected: GitReflogEntry) =
        GitReflogSelection(GitReflogRef.HEAD, entries, selected.toList())

    private companion object {
        val STASH = GitReflogRef("refs/stash")

        val amend = entry("HEAD@{0}", "a1b2c3d4e5")
        val commit = entry("HEAD@{1}", "b2c3d4e5f6")
        val reset = entry("HEAD@{2}", "c3d4e5f6a7")
        val checkout = entry("HEAD@{3}", "d4e5f6a7b8")
        val oldest = entry("HEAD@{4}", "e5f6a7b8c9")
        val entries = listOf(amend, commit, reset, checkout, oldest)

        fun entry(selector: String, hash: String) = GitReflogEntry(
            selector = selector,
            hash = hash,
            timestamp = 0L,
            action = "commit",
            description = "",
            author = "Alex Smith",
            subject = "Parse the separator",
        )
    }
}
