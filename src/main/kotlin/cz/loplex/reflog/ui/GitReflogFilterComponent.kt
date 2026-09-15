package cz.loplex.reflog.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Condition
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

    /** The popup this filter has up, if it has one. */
    private var openPopup: JBPopup? = null

    /** When this filter's popup was last dismissed, against which a fresh opening is measured. */
    private var popupClosedAt = 0L

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

    /**
     * Opens the popup, unless the click that asked for it is the one that has just dismissed it.
     *
     * The popup opens on the value the filter is showing where it offers one - see [isCurrentValue] - rather than
     * on its first item.
     *
     * A click on a filter whose popup is up closes that popup first - it is a click outside the popup, after all
     * - and only then reaches here. Opening one again at that point puts the popup back on the same click that
     * dismissed it, which reads as the popup flickering, or as a mouse that double-clicked on its own. Swing's
     * own combo box popups guard against exactly this, by remembering when theirs was last closed.
     *
     * Two guards, because a popup asked for twice over can arrive either way round: one for an opening while the
     * previous popup is still up, one for an opening that treads on the heels of its closing.
     */
    private fun showPopup() {
        if (!shouldOpenPopup(System.currentTimeMillis(), popupClosedAt, openPopup?.isDisposed == false)) return

        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                null,
                ActionGroupUtil.forceRecursiveUpdateInBackground(createActionGroup()),
                DataManager.getInstance().getDataContext(this),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                false,
                null,
                -1,
                Condition { isCurrentValue(it) },
                null,
            )
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                popupClosedAt = System.currentTimeMillis()
                openPopup = null
            }
        })
        openPopup = popup
        popup.showUnderneathOf(this)
    }

    companion object {
        /**
         * Which item a popup opens on: the one standing for the value the filter is showing.
         *
         * A decision of its own so that it can be asked without a popup to ask it of, and so that it is asked in
         * the one place all three filters open their popup from. A filter that ticks any number of its items
         * rather than standing for one of them offers no such item, and its popup opens where it always did.
         */
        fun isCurrentValue(action: AnAction): Boolean =
            action is GitReflogValueToggle<*> && action.isCurrent()

        /** What [FilterComponent] itself puts between the name and the value of a filter that counts as set. */
        const val NAME_SEPARATOR = ": "

        /**
         * Whether a popup asked for now is one to open, or the tail of the click that has just dismissed one.
         *
         * A decision of its own so that it can be asked without a popup to ask it of - the two ways a popup is
         * asked for twice over being easy to get one way round and not the other.
         */
        fun shouldOpenPopup(now: Long, closedAt: Long, oneIsOpen: Boolean): Boolean =
            !oneIsOpen && now - closedAt >= REOPEN_GUARD_MS

        /**
         * How soon after a popup closed an opening is taken to be the same click that closed it.
         *
         * Long enough to cover a click travelling from the popup's dismissal to this component, short enough that
         * a user who closed a popup and meant to open it again is never refused.
         */
        const val REOPEN_GUARD_MS = 200L
    }
}
