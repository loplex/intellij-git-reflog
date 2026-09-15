package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogRepositoryTest
import cz.loplex.reflog.ui.GitReflogDataKeys

/**
 * Covers when the actions that act on a reflog entry are offered.
 *
 * Checkout Revision, New Branch from Here and Reset Current Branch to Here all move to a single point in
 * history, so all three ask the same question of the context: exactly one entry, in a repository, in a project.
 * (Show Diff used to be among them, and now follows Compare instead, which answers a selection of any size.) Nothing about them says so at the point of use - the rule lives once, in the base class - which is
 * what makes it worth pinning down: relaxing it would leave "reset to this entry" offered for a selection with
 * no single "this" in it, and nothing would say a word.
 */
class GitReflogEntryActionTest : GitReflogRepositoryTest() {

    fun `test an action over one selected entry is offered`() {
        commit("a.txt", "one", "Add a")

        assertTrue(
            "An action over a single entry is not offered",
            updated(GitReflogCheckoutRevisionAction(), contextOf(entries().take(1))).isEnabled,
        )
    }

    /**
     * Several entries have no single point in history between them. The comparisons are what answer a selection
     * of several; these do not, and say so by greying rather than by acting on whichever entry comes first.
     */
    fun `test an action over several selected entries is not offered`() {
        commit("a.txt", "one", "Add a")
        commit("b.txt", "two", "Add b")

        val several = entries().take(2)
        assertEquals("The test did not select two entries", 2, several.size)

        assertFalse(
            "Checkout Revision is offered for a selection with no single entry in it",
            updated(GitReflogCheckoutRevisionAction(), contextOf(several)).isEnabled,
        )
        assertFalse(
            "New Branch from Here is offered for a selection with no single entry in it",
            updated(GitReflogNewBranchAction(), contextOf(several)).isEnabled,
        )
        assertFalse(
            "Reset Current Branch to Here is offered for a selection with no single entry in it",
            updated(GitReflogResetAction(), contextOf(several)).isEnabled,
        )
    }

    fun `test an action over no selected entry is not offered`() {
        commit("a.txt", "one", "Add a")

        assertFalse(
            "An action is offered with nothing selected",
            updated(GitReflogCheckoutRevisionAction(), contextOf(emptyList())).isEnabled,
        )
    }

    /**
     * The actions reach the Find Action dialog as well as the tab's own menu, where none of the three things
     * they need is in the context - so the answer there has to be the same as for an empty selection.
     */
    fun `test an action away from the tab is not offered`() {
        commit("a.txt", "one", "Add a")

        val withoutRepository = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(GitReflogDataKeys.SELECTED_ENTRIES, entries().take(1))
            .build()

        assertFalse(
            "An action is offered with no repository in the context",
            updated(GitReflogCheckoutRevisionAction(), withoutRepository).isEnabled,
        )
        assertFalse(
            "An action is offered with nothing of the tab in the context",
            updated(GitReflogCheckoutRevisionAction(), SimpleDataContext.EMPTY_CONTEXT).isEnabled,
        )
    }

    private fun entries(): List<GitReflogEntry> = reflog()

    private fun contextOf(selected: List<GitReflogEntry>): DataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(GitReflogDataKeys.REPOSITORY, repository)
        .add(GitReflogDataKeys.SELECTED_ENTRIES, selected)
        .build()
}
