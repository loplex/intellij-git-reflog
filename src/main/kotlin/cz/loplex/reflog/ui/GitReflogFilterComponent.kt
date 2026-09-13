package cz.loplex.reflog.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.FilterComponent
import java.util.function.Supplier

/**
 * A toolbar filter drawn the way the Log tab draws its own.
 *
 * [FilterComponent] is the platform's rendering of a filter - the rounded frame, the hovering, the name in front
 * of the value - and the Log's Branch, User and Date filters are all built on it. The Reflog tab sits next to the
 * Log and complements it, so the same filter has to look the same in both.
 *
 * Unlike the Log's, every filter here always has a value worth showing: there is one repository being read, one
 * ref being shown, and some set of action kinds getting through. None of them has the Log's unset state, where
 * the name stands alone and a reset button clears it, so none of them ever counts as set - the button stays a
 * drop-down arrow, and the popup is where a filter is put back.
 *
 * Which is also why the separator between the name and the value is supplied here: [FilterComponent] writes it
 * only for a filter that counts as set, so without this the toolbar would read "RefHEAD". Carrying it in the name
 * keeps the name and the value two labels, each drawn in the colour the platform gives it, rather than one string.
 *
 * The popup is opened exactly as the Log opens its own, down to the speed search. Only the Log's wrapper for
 * putting such a component in a toolbar is off limits, being marked internal, so the tab adds the components to
 * its toolbar row itself.
 */
internal abstract class GitReflogFilterComponent(name: Supplier<String>) :
    FilterComponent(Supplier { name.get() + NAME_SEPARATOR }) {

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

    final override fun isValueSelected(): Boolean = false

    final override fun getEmptyFilterValue(): String = ""

    final override fun createResetAction(): Runnable = Runnable { }

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

    private companion object {
        /** What [FilterComponent] itself puts between the name and the value of a filter that counts as set. */
        const val NAME_SEPARATOR = ": "
    }
}
