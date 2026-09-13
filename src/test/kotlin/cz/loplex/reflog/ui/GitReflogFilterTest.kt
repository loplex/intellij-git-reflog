package cz.loplex.reflog.ui

import cz.loplex.reflog.GitReflogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class GitReflogFilterTest {

    @Test
    fun `empty filter keeps everything`() {
        assertEquals(entries, filtered(GitReflogFilter()))
    }

    @Test
    fun `text is matched against description, action and selector`() {
        assertEquals(listOf(checkout), filtered(GitReflogFilter(text = "feature")))
        assertEquals(listOf(amend), filtered(GitReflogFilter(text = "amend")))
        assertEquals(listOf(reset), filtered(GitReflogFilter(text = "HEAD@{2}")))
    }

    @Test
    fun `text is matched against the subject and the author of the commit`() {
        assertEquals(listOf(commit), filtered(GitReflogFilter(text = "reader now reads")))
        assertEquals(listOf(checkout), filtered(GitReflogFilter(text = "Dana")))
    }

    @Test
    fun `text is matched against the beginning of the hash only`() {
        assertEquals(listOf(amend), filtered(GitReflogFilter(text = "a1b2")))
        // "1b2c3" sits inside the hash of the amend entry but starts none of them.
        assertEquals(emptyList<GitReflogEntry>(), filtered(GitReflogFilter(text = "1b2c3")))
    }

    @Test
    fun `text is matched ignoring case and surrounding space`() {
        assertEquals(listOf(checkout), filtered(GitReflogFilter(text = "  FEATURE ")))
    }

    @Test
    fun `an action kind keeps the variants of that operation`() {
        // "commit (amend)" is a commit, and picking the kind must not drop it.
        assertEquals(listOf(amend, commit), filtered(GitReflogFilter(actionKinds = setOf("commit"))))
    }

    @Test
    fun `several action kinds are kept together`() {
        assertEquals(listOf(amend, commit, reset), filtered(GitReflogFilter(actionKinds = setOf("commit", "reset"))))
    }

    @Test
    fun `text and action kind both have to match`() {
        assertEquals(emptyList<GitReflogEntry>(), filtered(GitReflogFilter("feature", setOf("commit"))))
    }

    private fun filtered(filter: GitReflogFilter) = entries.filter(filter::matches)

    private companion object {
        val amend = entry("HEAD@{0}", "a1b2c3d4e5", "commit (amend)", "rework the parser", "Parse the separator")
        val commit = entry("HEAD@{1}", "b2c3d4e5f6", "commit", "add the reader", "The reader now reads refs")
        val reset = entry("HEAD@{2}", "c3d4e5f6a7", "reset", "moving to HEAD~1", "Add the panel")
        val checkout = entry("HEAD@{3}", "d4e5f6a7b8", "checkout", "moving from master to feature", "Start the tab", "Dana Novak")
        val entries = listOf(amend, commit, reset, checkout)

        fun entry(
            selector: String,
            hash: String,
            action: String,
            description: String,
            subject: String,
            author: String = "Alex Smith",
        ) = GitReflogEntry(selector, hash, timestamp = 0L, action = action, description = description, author = author, subject = subject)
    }
}
