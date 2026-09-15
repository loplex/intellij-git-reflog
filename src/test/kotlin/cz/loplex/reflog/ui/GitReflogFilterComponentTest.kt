package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import cz.loplex.reflog.actions.updated
import java.util.function.Supplier

/**
 * Covers the answers the tab's filters give the platform - the three that decide how a filter is drawn, the guard
 * on opening its popup, and which item that popup opens on.
 *
 * Every filter here always has a value worth showing - one repository, one ref, some set of action kinds - so
 * none of them has the unset state the Log's filters have, where the name stands alone and a reset button clears
 * it. Saying that none of them counts as set is what keeps the button a drop-down arrow.
 */
class GitReflogFilterComponentTest : BasePlatformTestCase() {

    /**
     * A filter that counted as set would be drawn with a reset button in place of its arrow - and pressing it
     * would ask for a state none of these filters has.
     */
    fun `test no filter of the tab counts as set`() {
        assertFalse("A filter counts as set and would be drawn with a reset button", filter().countsAsSet())
    }

    /** There is no empty state to name, every filter always standing for some value. */
    fun `test no filter has an empty value to show`() {
        assertEquals("", filter().emptyFilterValue)
    }

    /** Nothing counts as set, so nothing is ever reset - the popup is where a filter is put back. */
    fun `test resetting a filter does nothing`() {
        filter().reset()
    }

    /** Redrawing after the state behind a filter has changed is what the tab calls to bring it up to date. */
    fun `test a filter redraws the listeners the platform installed on it`() {
        val filter = filter()
        var redrawn = 0
        filter.installChangeListener { redrawn++ }

        filter.filterChanged()

        assertEquals("The filter did not redraw", 1, redrawn)
    }

    /**
     * A click on a filter whose popup is up closes that popup first, being a click outside it, and only then
     * reaches the component that opens one. Opening again at that point puts the popup back on the very click
     * that dismissed it, which reads as a flicker - or as a mouse that has taken to double-clicking.
     */
    fun `test a popup is not reopened by the click that closed it`() {
        assertFalse(
            "A popup would be put back by the click that dismissed it",
            GitReflogFilterComponent.shouldOpenPopup(now = 1_000, closedAt = 950, oneIsOpen = false),
        )
    }

    /** A second popup over one that is still up is the same thing arriving the other way round. */
    fun `test a second popup is not opened over one already up`() {
        assertFalse(
            "A popup would be opened over a popup",
            GitReflogFilterComponent.shouldOpenPopup(now = 10_000, closedAt = 0, oneIsOpen = true),
        )
    }

    /** Closing a popup and meaning to open it again is a thing a user does, and is never refused. */
    fun `test a popup asked for later is opened`() {
        assertTrue(
            "A filter refuses to open its popup a second time",
            GitReflogFilterComponent.shouldOpenPopup(now = 10_000, closedAt = 1_000, oneIsOpen = false),
        )
    }

    /**
     * A popup of plain actions opens on its first item, so a filter standing on anything but its first value
     * would move the moment the user pressed Enter without meaning to move at all.
     */
    fun `test a popup opens on the value its filter is showing`() {
        assertTrue(
            "The popup would open on its first item rather than on the value being shown",
            GitReflogFilterComponent.isCurrentValue(toggle("stash", current = "stash")),
        )
    }

    /** Every other value is one the popup could be moved to, not the one it opens on. */
    fun `test a popup does not open on a value its filter is not showing`() {
        assertFalse(
            "The popup would open on a value other than the one being shown",
            GitReflogFilterComponent.isCurrentValue(toggle("HEAD", current = "stash")),
        )
    }

    /**
     * A filter that ticks any number of its items - the action kinds - stands for no single value, so there is
     * nothing for its popup to open on and it opens where it always did.
     */
    fun `test a popup of a filter standing for no single value opens where it did`() {
        assertFalse(
            "An item of a multiple-choice filter was taken for a current value",
            GitReflogFilterComponent.isCurrentValue(DumbAwareAction.create("commit") { }),
        )
    }

    /** Picking a value is what switches the filter to it. */
    fun `test picking a value switches the filter to it`() {
        val picked = mutableListOf<String>()
        val toggle = GitReflogValueToggle("HEAD", "HEAD", current = { "stash" }, select = { picked += it })

        toggle.setSelected(event(), true)

        assertEquals(listOf("HEAD"), picked)
    }

    /**
     * The value on screen is picked by pressing Enter on the item that carries the tick, which turns the tick
     * off. There is no state in which the filter stands for nothing, so that is the user confirming what is
     * already shown.
     */
    fun `test picking the value already shown leaves it alone`() {
        val picked = mutableListOf<String>()
        val toggle = GitReflogValueToggle("stash", "stash", current = { "stash" }, select = { picked += it })

        toggle.setSelected(event(), false)

        assertEmpty(picked)
    }

    /**
     * A popup keeps itself open over a toggle it has just performed, which is what the action kinds want and the
     * opposite of what a filter standing for one value wants: picking the value is the end of the visit.
     */
    fun `test picking a value closes the popup it was picked from`() {
        val presentation = updated(toggle("HEAD", current = "stash"), DataContext.EMPTY_CONTEXT)

        assertEquals(KeepPopupOnPerform.Never, presentation.keepPopupOnPerform)
    }

    private fun toggle(value: String, current: String) =
        GitReflogValueToggle(value, value, current = { current }, select = { })

    /** A bare event, a value toggle reading nothing out of the context it is performed in. */
    private fun event() = AnActionEvent.createEvent(
        DataContext.EMPTY_CONTEXT,
        Presentation(),
        ActionPlaces.UNKNOWN,
        ActionUiKind.POPUP,
        null,
    )

    private fun filter() = TestFilter()

    /**
     * A filter of the tab, standing in for the three real ones.
     *
     * Two of the answers under test are protected on the component, which is where they belong - the platform is
     * the only caller that has any business asking. A subclass reads them back rather than the component being
     * widened for the sake of being looked at.
     */
    private class TestFilter : GitReflogFilterComponent(Supplier { "Ref" }) {
        override fun getCurrentText(): String = "HEAD"

        override fun createActionGroup(): ActionGroup = DefaultActionGroup()

        fun countsAsSet(): Boolean = isValueSelected

        fun reset() = createResetAction().run()
    }
}
