package cz.loplex.reflog.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.FilterComponent
import java.util.function.Supplier

/**
 * A filter drawn the way the Log tab draws its own.
 *
 * [FilterComponent] is the platform's rendering of a filter - the rounded frame, the hovering, the name in front
 * of the value, the reset button that appears once something is picked - and the Log's Branch, User and Date
 * filters are all built on it. The Reflog tab sits next to the Log and complements it, so the same filter has to
 * look the same in both.
 *
 * The popup is opened exactly as the Log opens its own, down to the speed search. Only the Log's wrapper for
 * putting such a component in a toolbar is off limits, being marked internal, so the tab adds the components to
 * its toolbar row itself.
 *
 * Note how the class joins the name to the value: the ": " between them is inserted only while
 * [isValueSelected] holds, so anything that always has a value to show has to supply that separator itself - see
 * [GitReflogSelectorComponent].
 */
internal abstract class GitReflogFilterComponent(name: Supplier<String>) : FilterComponent(name) {

    private val changeListeners = mutableListOf<Runnable>()

    init {
        setShowPopupAction(Runnable { showPopup() })
    }

    /** What the popup offers. Rebuilt on every opening, so it can reflect what has been read by then. */
    protected abstract fun createActionGroup(): ActionGroup

    /** Redraws the component after the state it shows has changed. */
    fun filterChanged() {
        changeListeners.forEach(Runnable::run)
    }

    final override fun installChangeListener(runnable: Runnable) {
        changeListeners.add(runnable)
    }

    private fun showPopup() {
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                null,
                ActionGroupUtil.forceRecursiveUpdateInBackground(createActionGroup()),
                DataManager.getInstance().getDataContext(this),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false,
            )
            .showUnderneathOf(this)
    }
}

/**
 * A picker drawn as a filter, for the two toolbar items that are not filters at all: the repository and the ref.
 *
 * Both always show a value - there is always one repository being read and one ref being shown - and neither has
 * an unset state to return to, so neither ever counts as set and the button stays a drop-down arrow rather than
 * becoming a reset.
 *
 * Which is also why the separator is carried in the name: [FilterComponent] writes it only for a filter that
 * counts as set, so a picker would otherwise read "RefHEAD". Putting it in the name keeps the name and the value
 * two labels, each drawn in the colour the platform gives it, rather than one string.
 */
internal abstract class GitReflogSelectorComponent(name: Supplier<String>) :
    GitReflogFilterComponent(Supplier { name.get() + NAME_SEPARATOR }) {

    final override fun isValueSelected(): Boolean = false

    final override fun getEmptyFilterValue(): String = ""

    final override fun createResetAction(): Runnable = Runnable { }

    private companion object {
        /** What [FilterComponent] puts between the name and the value of a filter that is set. */
        const val NAME_SEPARATOR = ": "
    }
}
