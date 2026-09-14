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
        val shown = modes?.effective

        // Nothing on offer is a state of its own, and the switch says so rather than naming the mode that was
        // picked: there is no comparison on screen for it to be naming.
        e.presentation.isEnabled = modes != null && modes.applicable.isNotEmpty()
        // Named as well as valued, the way the tab's own filters read "Ref: HEAD": on its own, "Reflog Step"
        // says nothing about what it is a choice between.
        e.presentation.text = shown
            ?.let { GitReflogBundle.message("reflog.diff.mode.label", titleOf(it)) }
            ?: GitReflogBundle.message("reflog.diff.mode.none")
        e.presentation.description = shown?.let(::descriptionOf)
    }

    /**
     * A combo box drops what it cannot offer unless told otherwise, and dropping is the one thing this list must
     * not do: a reading that is merely absent leaves nothing to explain itself.
     */
    public override fun shouldShowDisabledActions(): Boolean = true

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup =
        DefaultActionGroup(GitReflogDiffMode.entries.map(::GitReflogDiffModeAction))

}

/**
 * The same readings on the file pane's context menu, in the same shape the switch gives them: every mode listed,
 * the ones the selection has no answer for greyed out.
 *
 * Listing only what fits would make for a shorter menu and a worse one. A set that changes with the selection
 * cannot be learnt, and a reading that is merely absent leaves nothing to explain itself - where a greyed one
 * says that it exists and that this selection is not for it.
 */
internal class GitReflogDiffModeGroup : ActionGroup(), DumbAware {

    init {
        isPopup = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val modes = e.getData(GitReflogDataKeys.DIFF_MODES)
        // Nothing fitting at all is the one case with no choice to offer - a stash reflog with several entries
        // selected - and four greyed readings would explain it no better than the empty pane already does.
        e.presentation.isEnabledAndVisible = modes != null && modes.applicable.isNotEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> =
        GitReflogDiffMode.entries.map { GitReflogDiffModeAction(it) }.toTypedArray()
}
