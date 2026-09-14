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

    /** Nothing fitting at all - a stash pair - leaves the submenu off the menu, as it leaves Compare off it. */
    fun `test the submenu is withheld where no reading fits`() {
        val stash = GitReflogSelection(STASH, entries, listOf(onBranch, onMaster))
        val modes = GitReflogDiffModes.of(GitReflogDiffMode.REFLOG_STEP, stash, GitReflogAncestry.DIVERGED)

        assertFalse(
            "The readings are offered for a selection none of them fits",
            updated(GitReflogShowDiffModeGroup(), contextOf(modes)).isEnabledAndVisible,
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
