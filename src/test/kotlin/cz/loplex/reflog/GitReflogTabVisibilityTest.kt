package cz.loplex.reflog

import cz.loplex.reflog.ui.GitReflogContentVisibilityPredicate

/**
 * The half of the tab's visibility rule that needs a repository to ask about.
 *
 * The rule reads the VCS mapping rather than the list of loaded repositories, on the reasoning that the mapping
 * is in place by the time the Git tool window is built where the repositories may not be. This is what says the
 * mapping really answers by then.
 */
class GitReflogTabVisibilityTest : GitReflogRepositoryTest() {

    fun `test the tab is offered to a project with Git in it`() {
        assertTrue(
            "The tab is not offered to a project whose VCS mapping is Git",
            GitReflogContentVisibilityPredicate().test(project),
        )
    }
}
