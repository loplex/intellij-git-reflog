package cz.loplex.reflog

import cz.loplex.reflog.GitReflogRef.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class GitReflogRefTest {

    @Test
    fun `ref names are sorted into kinds`() {
        assertEquals(Kind.HEAD, GitReflogRef("HEAD").kind)
        assertEquals(Kind.LOCAL_BRANCH, GitReflogRef("refs/heads/master").kind)
        assertEquals(Kind.REMOTE_BRANCH, GitReflogRef("refs/remotes/origin/HEAD").kind)
        assertEquals(Kind.STASH, GitReflogRef("refs/stash").kind)
        assertEquals(Kind.OTHER, GitReflogRef("refs/notes/commits").kind)
    }

    @Test
    fun `branches are presented without their ref prefix`() {
        assertEquals("master", GitReflogRef("refs/heads/master").presentableName)
        assertEquals("feature/parser", GitReflogRef("refs/heads/feature/parser").presentableName)
        assertEquals("origin/main", GitReflogRef("refs/remotes/origin/main").presentableName)
        assertEquals("stash", GitReflogRef("refs/stash").presentableName)
    }

    @Test
    fun `a ref of no known kind keeps its full name`() {
        assertEquals("refs/notes/commits", GitReflogRef("refs/notes/commits").presentableName)
    }

    @Test
    fun `refs are ordered by kind first and by presentable name within a kind`() {
        val refs = listOf(
            GitReflogRef("refs/stash"),
            GitReflogRef("refs/remotes/origin/main"),
            GitReflogRef("refs/heads/topic"),
            GitReflogRef("refs/heads/master"),
            GitReflogRef.HEAD,
        )

        assertEquals(
            listOf("HEAD", "master", "topic", "origin/main", "stash"),
            refs.sortedWith(GitReflogRef.ORDER).map { it.presentableName },
        )
    }
}
