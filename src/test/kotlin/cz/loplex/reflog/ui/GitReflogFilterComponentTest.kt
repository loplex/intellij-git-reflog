package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.function.Supplier

/**
 * Covers the three answers the tab's filters give the platform's filter rendering, which between them are why
 * the filters are drawn as they are.
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
