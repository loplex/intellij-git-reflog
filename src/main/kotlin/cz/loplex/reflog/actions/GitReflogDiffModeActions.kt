package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogDiffMode
import cz.loplex.reflog.GitReflogDiffModes
import cz.loplex.reflog.ui.GitReflogDataKeys
import javax.swing.JComponent

/** Name of [mode] as the toolbar and the menus write it. */
internal fun titleOf(mode: GitReflogDiffMode): String = GitReflogBundle.message(
    when (mode) {
        GitReflogDiffMode.REFLOG_STEP -> "reflog.diff.mode.step"
        GitReflogDiffMode.BETWEEN_SELECTED -> "reflog.diff.mode.between"
        GitReflogDiffMode.UNION -> "reflog.diff.mode.union"
        GitReflogDiffMode.WORKING_TREE -> "reflog.diff.mode.working.tree"
    },
)

/** What [mode] compares, as the tooltip and the menu description say it. */
internal fun descriptionOf(mode: GitReflogDiffMode): String = GitReflogBundle.message(
    when (mode) {
        GitReflogDiffMode.REFLOG_STEP -> "reflog.diff.mode.step.description"
        GitReflogDiffMode.BETWEEN_SELECTED -> "reflog.diff.mode.between.description"
        GitReflogDiffMode.UNION -> "reflog.diff.mode.union.description"
        GitReflogDiffMode.WORKING_TREE -> "reflog.diff.mode.working.tree.description"
    },
)

/**
 * Picks one reading of the selected entries.
 *
 * Ticked for the mode the file pane is *showing* rather than the one that was picked, so that a selection the
 * picked mode has no answer for - which is answered by the nearest mode that does - still says which comparison
 * is on screen.
 */
internal class GitReflogDiffModeAction(private val mode: GitReflogDiffMode) : ToggleAction(), DumbAware {

    init {
        templatePresentation.text = titleOf(mode)
        templatePresentation.description = descriptionOf(mode)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val modes = e.getData(GitReflogDataKeys.DIFF_MODES)
        e.presentation.isEnabled = modes != null && mode in modes.applicable
    }

    override fun isSelected(e: AnActionEvent): Boolean = e.getData(GitReflogDataKeys.DIFF_MODES)?.effective == mode

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) e.getData(GitReflogDataKeys.PANEL)?.diffMode = mode
    }
}

/**
 * The switch on the file pane's toolbar, showing which of the readings is on screen.
 *
 * Every mode is listed, the ones the selection has no answer for among them: a switch that dropped them would
 * leave the user unable to see that the reading they want exists and what it would take to get it. What such a
 * mode does not do is silently stay picked - the pane shows the nearest mode that fits, and the switch says so.
 */
internal class GitReflogDiffModeSwitch : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val modes = e.getData(GitReflogDataKeys.DIFF_MODES)
        e.presentation.isEnabled = modes != null
        e.presentation.text = shownMode(modes)?.let(::titleOf) ?: GitReflogBundle.message("reflog.diff.mode.none")
        e.presentation.description = shownMode(modes)?.let(::descriptionOf)
    }

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup =
        DefaultActionGroup(GitReflogDiffMode.entries.map(::GitReflogDiffModeAction))

    /**
     * What the button reads: the mode on screen, or, while nothing is selected for it to be on screen for, the
     * one that was picked. The button says what the pane would do rather than going blank between selections.
     */
    private fun shownMode(modes: GitReflogDiffModes?): GitReflogDiffMode? =
        modes?.let { it.effective ?: it.preferred }
}

/**
 * The same readings on the file pane's context menu, where only the ones the selection has an answer for are
 * listed: a menu is read top to bottom and shown on demand, so it can be exactly as long as the moment calls for,
 * while the toolbar switch has to keep its full shape to stay a switch.
 */
internal class GitReflogDiffModeGroup : ActionGroup(), DumbAware {

    init {
        isPopup = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val modes = e.getData(GitReflogDataKeys.DIFF_MODES)
        // A single applicable mode is not a choice, and a submenu offering it would only be in the way.
        e.presentation.isEnabledAndVisible = modes != null && modes.applicable.size > 1
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val modes = e?.getData(GitReflogDataKeys.DIFF_MODES) ?: return AnAction.EMPTY_ARRAY
        return modes.applicable.map { GitReflogDiffModeAction(it) }.toTypedArray()
    }
}
