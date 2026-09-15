package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/**
 * One value offered by a filter that stands for a single value - the repository being read, the ref being shown.
 *
 * A popup of plain actions has no notion of a current value: it draws no tick against any of them, and opens on
 * its first item however far from it the value on screen is. Pressing Enter without moving then picks the first
 * item rather than confirming what is already shown, and arrow keys count from the top rather than from where the
 * user stands. Every other selector in the IDE opens on its current value, so this one has to as well.
 *
 * A [ToggleAction] carries the tick, which is the same shape the Compare switch on the file pane already uses.
 * The preselection is asked of [isCurrent] instead, the popup deciding which item to open on before it has any
 * event to ask [isSelected] with.
 *
 * [current] is read on every asking rather than captured, the group being built once per opening but the value
 * behind it changing whenever a filter is used.
 */
internal class GitReflogValueToggle<T : Any>(
    private val value: T,
    text: String,
    private val current: () -> T?,
    private val select: (T) -> Unit,
) : ToggleAction(text), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    /**
     * Closes the popup on being picked.
     *
     * A popup keeps itself open over a toggle it has just performed, which is right for the action kinds, where
     * several are ticked in one visit, and wrong here: there is one value to pick, and picking it is the end of
     * the visit. Plain actions closed the popup by themselves, so saying so is the price of the tick.
     */
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    /** Whether this is the value the filter is showing, asked without an event so a popup can ask it too. */
    fun isCurrent(): Boolean = value == current()

    override fun isSelected(e: AnActionEvent): Boolean = isCurrent()

    /**
     * Switches to this value, and does nothing when it is switched off.
     *
     * A tick is turned off by picking the item that carries it, which here is the user confirming what is already
     * shown - there is no state in which none of these values is the current one, so the only answer is to leave
     * it as it is.
     */
    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (state) select(value)
    }
}
