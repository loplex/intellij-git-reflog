package cz.loplex.reflog.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil

/**
 * Covers that the filter field remembers what was looked for, a reflog being where one goes back to look for the
 * same lost commit twice.
 *
 * The popup the history is offered in is the platform's own; what is the tab's to get right is that the field is
 * built to keep a history at all, that Enter adds to it, and that it is kept under a name of the tab's own rather
 * than only for as long as the field lives.
 */
class GitReflogSearchFieldTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            // The history is kept in the application's properties, which outlive this test as they would a session.
            PropertiesComponent.getInstance().unsetValue(GitReflogPanel.SEARCH_HISTORY)
        } finally {
            super.tearDown()
        }
    }

    /**
     * The table narrows as the text is typed, so Enter has nothing left to apply - which leaves it meaning what
     * it means in the platform's own search fields.
     *
     * What is asserted is the application's own properties rather than the field's list: the field keeps a list
     * either way, and it is the writing of it under a name that makes the search outlast the session.
     */
    fun `test Enter writes the search where the next session will read it`() {
        val field = searchFieldOf(newPanel())

        field.text = "abandoned"
        pressEnter(field)

        val kept = PropertiesComponent.getInstance().getValue(GitReflogPanel.SEARCH_HISTORY)
        assertNotNull("Enter left nothing behind for the next session", kept)
        assertTrue("The search is not among what was kept: $kept", kept!!.contains("abandoned"))
    }

    /**
     * A second panel reading the history back is what a restarted IDE does: the field is built from the
     * application's properties, so what was written there is what the next session offers.
     */
    fun `test the next session is offered every search, the latest one first`() {
        val first = newPanel()
        searchFieldOf(first).let {
            it.text = "abandoned"
            pressEnter(it)
            it.text = "rebased"
            pressEnter(it)
        }
        Disposer.dispose(first)

        assertEquals(listOf("rebased", "abandoned"), searchFieldOf(newPanel()).history)
    }

    /**
     * Only Enter records a search. Recording every keystroke would fill the popup with the prefixes typed on the
     * way to a search rather than with the searches themselves.
     */
    fun `test typing alone leaves the history empty`() {
        val first = newPanel()
        searchFieldOf(first).text = "abandoned"
        Disposer.dispose(first)

        val offered = searchFieldOf(newPanel()).history
        assertTrue("Typing alone filled the history: $offered", offered.isEmpty())
    }

    private fun newPanel(): GitReflogPanel {
        val panel = GitReflogPanel(project)
        Disposer.register(testRootDisposable, panel)
        return panel
    }

    private fun searchFieldOf(panel: GitReflogPanel): SearchTextField =
        UIUtil.findComponentOfType(panel, SearchTextField::class.java)
            ?: throw AssertionError("The tab has no filter field")

    /** What the field sees when Enter is pressed in it, whatever the keymap binds Enter to. */
    private fun pressEnter(field: SearchTextField) = field.textEditor.postActionEvent()
}
