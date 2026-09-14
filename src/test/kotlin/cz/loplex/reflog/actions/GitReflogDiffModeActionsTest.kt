package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
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
 * Covers what the switch and the context menu say about a selection, which is the half of Compare that no test of
 * the rules can reach: the rules decide which modes fit, these decide what the user is shown of that decision.
 *
 * The snapshot is built through [GitReflogDiffModes.of], the same call the tab publishes into the action system,
 * so a selection described here stands for the selection the table would have made.
 */
class GitReflogDiffModeActionsTest : BasePlatformTestCase() {

    /**
     * Two entries from branches that never met: the comparisons reading them as one timeline have an answer,
     * the two that do not are offered greyed rather than dropped.
     */
    fun `test a diverged pair greys the readings it has no answer for`() {
        val modes = modesFor(GitReflogDiffMode.REFLOG_STEP, GitReflogAncestry.DIVERGED, onBranch, onMaster)

        assertEnabled("Reflog Step has both ends of the selection", GitReflogDiffMode.REFLOG_STEP, modes)
        assertEnabled("Between Selected has both ends of the selection", GitReflogDiffMode.BETWEEN_SELECTED, modes)
        // Merging the diffs of commits that sit on branches which never met undoes itself.
        assertDisabled("Selected Commits spans a fork", GitReflogDiffMode.UNION, modes)
        // The working tree is compared against one state, and two are selected.
        assertDisabled("Against Working Tree has two states", GitReflogDiffMode.WORKING_TREE, modes)
    }

    /** All four readings stay on the menu whichever of them fits, so that an absent one never goes unexplained. */
    fun `test every reading is listed whether or not it fits`() {
        val modes = modesFor(GitReflogDiffMode.REFLOG_STEP, GitReflogAncestry.DIVERGED, onBranch, onMaster)

        assertEquals("The menu lists fewer readings than exist", GitReflogDiffMode.entries.size, childrenOf(modes).size)
        // A combo box drops what it cannot offer unless told otherwise, and dropping is what must not happen here:
        // a reading that is merely absent leaves nothing to explain itself.
        assertTrue("The switch would drop what it cannot offer", GitReflogDiffModeSwitch().shouldShowDisabledActions())
    }

    /**
     * The switch names the reading on screen - in short, the file pane's toolbar being as narrow as the file
     * pane, with the full name kept for the tooltip and for the menu.
     */
    fun `test the switch names the reading it is showing, in short`() {
        val modes = modesFor(GitReflogDiffMode.REFLOG_STEP, GitReflogAncestry.DIVERGED, onBranch, onMaster)

        val presentation = updated(GitReflogDiffModeSwitch(), modes)
        assertTrue("The switch is greyed for a selection two readings fit", presentation.isEnabled)
        assertEquals("Reflog Step", presentation.text)
        // No icon: nothing in the icon set reads as "what these files are being compared as" rather than as a
        // diff to be opened, and the drop-down arrow already says that this is a choice.
        assertNull("The switch carries an icon that would read as a diff to open", presentation.icon)
    }

    /**
     * What the short name leaves out is in the tooltip: the full name, said as the choice it is, and what the
     * reading actually compares.
     */
    fun `test the tooltip carries the full name and what it compares`() {
        val modes = modesFor(GitReflogDiffMode.WORKING_TREE, GitReflogAncestry.LINEAR, onMaster)

        val description = updated(GitReflogDiffModeSwitch(), modes).description
        assertNotNull("The switch says nothing on hover", description)
        assertTrue("The tooltip does not name the reading in full: $description", "Compare: Against Working Tree" in description!!)
        assertTrue("The tooltip does not say what is compared: $description", "working tree" in description)
    }

    /** The menu has the room the switch has not, and keeps the names a reading is first met under. */
    fun `test the menu keeps the full names`() {
        val modes = modesFor(GitReflogDiffMode.REFLOG_STEP, GitReflogAncestry.LINEAR, onMaster)

        assertEquals(
            listOf("Reflog Step", "Between Selected", "Selected Commits", "Against Working Tree"),
            childrenOf(modes).map { updated(it, contextOf(modes)).text },
        )
    }

    /**
     * Several stash entries are read as what they hold between them, the readings that ask what a movement did
     * having nothing to say about a stack of unrelated entries.
     */
    fun `test several stash entries are read as what they hold`() {
        val stash = GitReflogSelection(STASH, entries, listOf(onBranch, onMaster))
        val modes = GitReflogDiffModes.of(GitReflogDiffMode.REFLOG_STEP, stash, GitReflogAncestry.DIVERGED)

        assertEquals("A stash pair is read as something else", listOf(GitReflogDiffMode.UNION), modes.applicable)
        assertEquals(GitReflogDiffMode.UNION, modes.effective)
        assertEquals("Commits", updated(GitReflogDiffModeSwitch(), modes).text)
    }

    /**
     * Nothing selected is the one case with no comparison to name - and the switch says so outright rather than
     * naming the mode that was picked, there being no comparison on screen for it to be naming.
     */
    fun `test an empty selection leaves nothing to compare`() {
        val modes = modesFor(GitReflogDiffMode.REFLOG_STEP, GitReflogAncestry.LINEAR)

        assertTrue("Something is on offer with nothing selected: ${modes.applicable}", modes.applicable.isEmpty())
        assertNull("A comparison is on screen with nothing selected", modes.effective)

        val presentation = updated(GitReflogDiffModeSwitch(), modes)
        assertFalse("The switch offers a reading it has none of", presentation.isEnabled)
        assertEquals("Nothing to Compare", presentation.text)

        // Four greyed readings would explain the case no better than the empty pane already does.
        assertFalse(
            "Compare is on the menu with nothing to offer",
            updated(GitReflogDiffModeGroup(), modes).isEnabledAndVisible,
        )
    }

    /**
     * The oldest entry of the reflog records a movement whose starting state the reflog no longer holds, so
     * Reflog Step has nothing to read there and the nearest reading that does is shown instead - without the
     * picked mode being rewritten, so that it comes back on the next selection that suits it.
     */
    fun `test the oldest entry falls back to a reading that has an answer`() {
        val selection = GitReflogSelection(GitReflogRef.HEAD, entries, listOf(oldest))
        val modes = GitReflogDiffModes.of(GitReflogDiffMode.REFLOG_STEP, selection, GitReflogAncestry.LINEAR)

        assertEquals("The picked mode was rewritten by the fallback", GitReflogDiffMode.REFLOG_STEP, modes.preferred)
        assertEquals(GitReflogDiffMode.UNION, modes.effective)
        assertEquals("Commits", updated(GitReflogDiffModeSwitch(), modes).text)

        // The tick follows what is on screen rather than what was picked, or the switch would name one reading
        // and tick another.
        assertTrue("Selected Commits is not ticked", isTicked(GitReflogDiffMode.UNION, modes))
        assertFalse("Reflog Step is ticked for a selection it has no answer for", isTicked(GitReflogDiffMode.REFLOG_STEP, modes))
        assertDisabled("Reflog Step reaches past the oldest entry", GitReflogDiffMode.REFLOG_STEP, modes)
    }

    /**
     * The menu closes on a pick, rather than staying open the way a toggle does by default.
     *
     * Four readings of which one is showing is a choice, not a set of boxes to tick, so there is nothing left to
     * do in the menu once one is picked. Left open it would also go on showing the tick where it stood when it
     * opened - nothing asks an open menu again - while the pane behind it had already changed.
     */
    fun `test picking a reading closes the menu`() {
        val modes = modesFor(GitReflogDiffMode.REFLOG_STEP, GitReflogAncestry.LINEAR, onMaster)

        assertEquals(
            "The menu would stay open on a pick, leaving its tick where it was",
            KeepPopupOnPerform.Never,
            updated(GitReflogDiffModeAction(GitReflogDiffMode.UNION), contextOf(modes)).keepPopupOnPerform,
        )
    }

    private fun modesFor(
        preferred: GitReflogDiffMode,
        ancestry: GitReflogAncestry,
        vararg selected: GitReflogEntry,
    ): GitReflogDiffModes =
        GitReflogDiffModes.of(preferred, GitReflogSelection(GitReflogRef.HEAD, entries, selected.toList()), ancestry)

    private fun assertEnabled(why: String, mode: GitReflogDiffMode, modes: GitReflogDiffModes) =
        assertTrue("$why, yet the reading is greyed", updated(GitReflogDiffModeAction(mode), modes).isEnabled)

    private fun assertDisabled(why: String, mode: GitReflogDiffMode, modes: GitReflogDiffModes) =
        assertFalse("$why, yet the reading is offered", updated(GitReflogDiffModeAction(mode), modes).isEnabled)

    private fun isTicked(mode: GitReflogDiffMode, modes: GitReflogDiffModes): Boolean =
        Toggleable.isSelected(updated(GitReflogDiffModeAction(mode), modes))

    private fun childrenOf(modes: GitReflogDiffModes): Array<AnAction> = GitReflogDiffModeGroup().getChildren(
        AnActionEvent.createEvent(contextOf(modes), Presentation(), ActionPlaces.UNKNOWN, ActionUiKind.POPUP, null),
    )

    /** The presentation [action] leaves behind once it has been asked about a selection, as a menu would ask it. */
    private fun updated(action: AnAction, modes: GitReflogDiffModes): Presentation =
        updated(action, contextOf(modes))

    private fun contextOf(modes: GitReflogDiffModes): DataContext =
        SimpleDataContext.getSimpleContext(GitReflogDataKeys.DIFF_MODES, modes)

    private companion object {
        val STASH = GitReflogRef("refs/stash")

        val onMaster = entry("HEAD@{0}", "a1b2c3d4e5")
        val onBranch = entry("HEAD@{1}", "b2c3d4e5f6")
        val earlier = entry("HEAD@{2}", "c3d4e5f6a7")
        val oldest = entry("HEAD@{3}", "d4e5f6a7b8")
        val entries = listOf(onMaster, onBranch, earlier, oldest)

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
