package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import cz.loplex.reflog.ui.GitReflogDataKeys
import cz.loplex.reflog.ui.GitReflogPanel

/**
 * One of the two buttons that place the diff pane, the pair the platform's own preview toolbars are built from.
 *
 * Between them they cover all three states in a single click: neither pressed hides the diff pane, either one
 * pressed shows it on that side, and pressing the one that is already down hides it again.
 */
internal abstract class GitReflogDiffPreviewLocationAction : ToggleAction(), DumbAware {

    /** Which side this button stands for. */
    protected abstract val atBottom: Boolean

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = panel(e) != null
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val panel = panel(e) ?: return false
        return panel.isDiffPreviewVisible && panel.isDiffPreviewAtBottom == atBottom
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val panel = panel(e) ?: return
        // The side is set first so that a pane being brought back opens where it is asked for, not where it was.
        if (state) panel.isDiffPreviewAtBottom = atBottom
        panel.isDiffPreviewVisible = state
    }

    private fun panel(e: AnActionEvent): GitReflogPanel? = e.getData(GitReflogDataKeys.PANEL)
}

/**
 * Puts the diff pane down the right-hand side of the tab.
 */
internal class GitReflogPreviewOnTheRightAction : GitReflogDiffPreviewLocationAction() {
    override val atBottom: Boolean get() = false
}

/**
 * Puts the diff pane across the bottom of the tab, which is where it starts: a diff is read across, so the width
 * of the whole tab suits it better than the half a side-by-side split would leave it.
 */
internal class GitReflogPreviewAtTheBottomAction : GitReflogDiffPreviewLocationAction() {
    override val atBottom: Boolean get() = true
}
