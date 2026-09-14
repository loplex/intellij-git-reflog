package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import cz.loplex.reflog.GitReflogAncestry
import cz.loplex.reflog.GitReflogDiffMode
import cz.loplex.reflog.GitReflogDiffModes
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogRef
import cz.loplex.reflog.GitReflogSelection
import cz.loplex.reflog.ui.GitReflogDataKeys

/**
 * Covers the submenu that opens a reading in the diff viewer.
 *
 * The file pane shows one reading at a time; this is how the other three are looked at without changing which
 * one it is showing. Which readings it offers has to agree with Compare down to the greying, the two being the
 * same question asked from two places - and what fits is a property of the selection, not of who is asking.
 */
class GitReflogShowDiffModesTest : BasePlatformTestCase() {

    fun `test the submenu is on the table's context menu, under Show Diff`() {
        val menu = ActionManager.getInstance().getAction("GitReflog.ContextMenu")
        val ids = idsOf(menu)

        assertTrue("Show Diff is not on the table's context menu: $ids", "GitReflog.ShowDiff" in ids)
        assertTrue("The readings are not on the table's context menu: $ids", "GitReflog.ShowDiffModes" in ids)
    }

    /**
     * Show Diff opens whatever the file pane is showing, so it answers a selection of any size - and says nothing
     * about which reading that is, the pane beside it being the answer.
     */
    fun `test Show Diff follows the reading the pane is showing`() {
        val several = modesFor(GitReflogAncestry.LINEAR, onMaster, onBranch)
        assertTrue(
            "Show Diff is refused a selection the file pane has an answer for",
            updated(GitReflogShowDiffAction(), contextOf(several)).isEnabled,
        )

        val nothingFits = modesFor(GitReflogAncestry.LINEAR)
        assertFalse(
            "Show Diff is offered where the file pane is showing nothing",
            updated(GitReflogShowDiffAction(), contextOf(nothingFits)).isEnabled,
        )
    }

    /**
     * Named for the reading it will open, rather than "Show Diff" on its own.
     *
     * The pane that would otherwise answer "which of the four?" is one of the things the tab lets you put away,
     * so the menu item has to carry the answer itself.
     */
    fun `test Show Diff is named for the reading it opens`() {
        val step = modesFor(GitReflogAncestry.LINEAR, onMaster)
        assertEquals("Show Diff: Reflog Step", updated(GitReflogShowDiffAction(), contextOf(step)).text)

        val oldest = GitReflogDiffModes.of(
            GitReflogDiffMode.WORKING_TREE,
            GitReflogSelection(GitReflogRef.HEAD, entries, listOf(onMaster)),
            GitReflogAncestry.LINEAR,
        )
        assertEquals("Show Diff: Against Working Tree", updated(GitReflogShowDiffAction(), contextOf(oldest)).text)
    }

    /** Away from the tab there is no reading to name, so the action falls back to its own plain name. */
    fun `test Show Diff keeps its plain name away from the tab`() {
        assertEquals(
            "Show Diff",
            updated(GitReflogShowDiffAction(), SimpleDataContext.EMPTY_CONTEXT).text,
        )
    }

    fun `test every reading can be opened in the diff viewer`() {
        assertEquals(
            GitReflogDiffMode.entries.map(::titleOf),
            GitReflogShowDiffModeGroup().getChildren(null).map { it.templatePresentation.text },
        )
    }

    /** The same greying as Compare: what a selection has no answer for cannot be opened either. */
    fun `test a reading with no answer cannot be opened`() {
        val modes = modesFor(GitReflogAncestry.DIVERGED, onBranch, onMaster)

        assertTrue(
            "Reflog Step spans the selection, yet cannot be opened",
            updated(GitReflogShowDiffInModeAction(GitReflogDiffMode.REFLOG_STEP), contextOf(modes)).isEnabled,
        )
        assertFalse(
            "Merging diverged commits can be opened, where Compare will not offer it",
            updated(GitReflogShowDiffInModeAction(GitReflogDiffMode.UNION), contextOf(modes)).isEnabled,
        )
        assertFalse(
            "The working tree can be opened against a selection of two",
            updated(GitReflogShowDiffInModeAction(GitReflogDiffMode.WORKING_TREE), contextOf(modes)).isEnabled,
        )
    }

    /** Nothing selected leaves the submenu off the menu, as it leaves Compare off it. */
    fun `test the submenu is withheld where no reading fits`() {
        assertFalse(
            "The readings are offered with nothing selected",
            updated(GitReflogShowDiffModeGroup(), contextOf(modesFor(GitReflogAncestry.LINEAR))).isEnabledAndVisible,
        )
    }

    /** A stash pair has one reading, so the submenu is there - with the three that do not fit greyed. */
    fun `test a stash pair is offered the reading that merges what it holds`() {
        val stash = GitReflogSelection(STASH, entries, listOf(onBranch, onMaster))
        val modes = GitReflogDiffModes.of(GitReflogDiffMode.REFLOG_STEP, stash, GitReflogAncestry.DIVERGED)

        assertTrue(
            "The readings are withheld for a stash pair, which has one",
            updated(GitReflogShowDiffModeGroup(), contextOf(modes)).isEnabledAndVisible,
        )
        assertTrue(
            "What the stash entries hold cannot be opened",
            updated(GitReflogShowDiffInModeAction(GitReflogDiffMode.UNION), contextOf(modes)).isEnabled,
        )
        assertFalse(
            "A stash is read as a timeline after all",
            updated(GitReflogShowDiffInModeAction(GitReflogDiffMode.REFLOG_STEP), contextOf(modes)).isEnabled,
        )
    }

    fun `test nothing can be opened away from the tab`() {
        assertFalse(
            "A reading can be opened with no tab in the context",
            updated(
                GitReflogShowDiffInModeAction(GitReflogDiffMode.REFLOG_STEP),
                SimpleDataContext.EMPTY_CONTEXT,
            ).isEnabled,
        )
    }

    private fun modesFor(ancestry: GitReflogAncestry, vararg selected: GitReflogEntry): GitReflogDiffModes =
        GitReflogDiffModes.of(
            GitReflogDiffMode.REFLOG_STEP,
            GitReflogSelection(GitReflogRef.HEAD, entries, selected.toList()),
            ancestry,
        )

    private fun contextOf(modes: GitReflogDiffModes): DataContext =
        SimpleDataContext.getSimpleContext(GitReflogDataKeys.DIFF_MODES, modes)

    private fun idsOf(action: com.intellij.openapi.actionSystem.AnAction): List<String> {
        val actionManager = ActionManager.getInstance()
        val id = actionManager.getId(action)
        val children = (action as? com.intellij.openapi.actionSystem.ActionGroup)
            ?.getChildren(null)?.flatMap { idsOf(it) }.orEmpty()
        return listOfNotNull(id) + children
    }

    private companion object {
        val STASH = GitReflogRef("refs/stash")

        val onMaster = entry("HEAD@{0}", "a1b2c3d4e5")
        val onBranch = entry("HEAD@{1}", "b2c3d4e5f6")
        val earlier = entry("HEAD@{2}", "c3d4e5f6a7")
        val entries = listOf(onMaster, onBranch, earlier)

        fun entry(selector: String, hash: String) = GitReflogEntry(
            selector = selector,
            hash = hash,
            timestamp = 0L,
            action = "commit",
            description = "",
            author = "Alex Smith",
            subject = "Work that never landed",
        )
    }
}
