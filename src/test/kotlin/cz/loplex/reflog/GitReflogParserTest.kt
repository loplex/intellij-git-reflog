package cz.loplex.reflog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the format the plugin asks `git reflog show` for. Every case here is a line git has been seen to
 * produce, so the expectations double as a record of what the format actually looks like.
 */
class GitReflogParserTest {

    @Test
    fun `a record is split into its fields`() {
        val entry = parse(0, HEAD, "a1b2c3d4e5f6", "HEAD@{1789253693}", "commit: add the reader", "Alex Smith", "Add the reader")!!

        assertEquals("a1b2c3d4e5f6", entry.hash)
        assertEquals("commit", entry.action)
        assertEquals("add the reader", entry.description)
        assertEquals("Alex Smith", entry.author)
        assertEquals("Add the reader", entry.subject)
    }

    @Test
    fun `the selector keeps the ref git printed and replaces the time with the position`() {
        assertEquals("HEAD@{0}", parseHead(0, "HEAD@{1789253693}", "commit: first")!!.selector)
        assertEquals("HEAD@{7}", parseHead(7, "HEAD@{1789253600}", "commit: later")!!.selector)
        // Whatever git puts in front of the "@{" is carried over as it stands.
        assertEquals("master@{2}", parse(2, MASTER, "h", "master@{1789253693}", "commit: on master", "A", "S")!!.selector)
    }

    @Test
    fun `the timestamp comes from the selector, in milliseconds`() {
        assertEquals(1_789_253_693_000L, parseHead(0, "HEAD@{1789253693}", "commit: first")!!.timestamp)
    }

    @Test
    fun `a selector without a readable time leaves the timestamp at zero`() {
        // Not a format git produces, but a reflog rewritten by a script can leave anything behind.
        assertEquals(0L, parseHead(0, "HEAD@{whenever}", "commit: first")!!.timestamp)
        assertEquals(0L, parseHead(0, "HEAD", "commit: first")!!.timestamp)
    }

    @Test
    fun `a subject without details is taken as an action of its own`() {
        val entry = parseHead(0, "HEAD@{1789253693}", "rebase finished")!!

        assertEquals("rebase finished", entry.action)
        assertEquals("", entry.description)
    }

    @Test
    fun `only the first colon of a subject separates the action`() {
        val entry = parseHead(0, "HEAD@{1789253693}", "merge feature: Fast-forward")!!

        assertEquals("merge feature", entry.action)
        assertEquals("Fast-forward", entry.description)
    }

    @Test
    fun `a stash subject is kept whole, because none of it is an action`() {
        val entry = parse(0, STASH, "h", "stash@{1789253693}", "WIP on master: eddeef8 first", "A", "S")!!

        assertEquals("", entry.action)
        assertEquals("WIP on master: eddeef8 first", entry.description)
    }

    @Test
    fun `a separator inside the commit subject stays in the subject`() {
        // The subject is the last field precisely so that a separator in it cannot shift any other field.
        val entry = parse(0, HEAD, "h", "HEAD@{1}", "commit: odd", "A", "Weird${SEPARATOR}message")!!

        assertEquals("A", entry.author)
        assertEquals("Weird${SEPARATOR}message", entry.subject)
    }

    @Test
    fun `a line missing fields is not a record`() {
        assertNull(GitReflogParser.parse(0, "", HEAD))
        assertNull(GitReflogParser.parse(0, "a1b2c3${SEPARATOR}HEAD@{1}${SEPARATOR}commit: first", HEAD))
    }

    private fun parseHead(index: Int, dateSelector: String, reflogSubject: String) =
        parse(index, HEAD, "a1b2c3d4e5f6", dateSelector, reflogSubject, "Alex Smith", "Add the reader")

    private fun parse(index: Int, ref: GitReflogRef, vararg fields: String) =
        GitReflogParser.parse(index, fields.joinToString(SEPARATOR.toString()), ref)

    private companion object {
        val SEPARATOR = GitReflogParser.FIELD_SEPARATOR
        val HEAD = GitReflogRef.HEAD
        val MASTER = GitReflogRef("refs/heads/master")
        val STASH = GitReflogRef("refs/stash")
    }
}
