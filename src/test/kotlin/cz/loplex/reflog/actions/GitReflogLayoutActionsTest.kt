package cz.loplex.reflog.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import cz.loplex.reflog.ui.GitReflogChangesPanel
import cz.loplex.reflog.ui.GitReflogDataKeys
import cz.loplex.reflog.ui.GitReflogPanel

/**
 * Covers the three buttons that say what is on screen below the table.
 *
 * Between them the two diff buttons carry three states - no diff pane, one down the right, one across the bottom -
 * with no button for the third. Which button is pressed, and what pressing an already-pressed one does, is the
 * whole of that arrangement, and it is worked out in the actions rather than in the panel they act on.
 */
class GitReflogLayoutActionsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            // Where the panes were left is kept in the application's properties, which outlive this test.
            val properties = PropertiesComponent.getInstance()
            properties.unsetValue(GitReflogChangesPanel.SHOW_FILE_PANE)
            properties.unsetValue(GitReflogChangesPanel.SHOW_DIFF_PREVIEW)
            properties.unsetValue(GitReflogChangesPanel.DIFF_PREVIEW_AT_BOTTOM)
        } finally {
            super.tearDown()
        }
    }

    fun `test the file pane button says whether the file pane is up`() {
        val panel = newPanel()
        val action = GitReflogShowFilesAction()

        panel.isFilePaneVisible = true
        assertTrue("The button is not pressed with the file pane up", isPressed(action, panel))

        panel.isFilePaneVisible = false
        assertFalse("The button is pressed with the file pane away", isPressed(action, panel))
    }

    fun `test pressing the file pane button puts the pane up and away`() {
        val panel = newPanel()
        val action = GitReflogShowFilesAction()

        press(action, panel, state = false)
        assertFalse("The file pane stayed up", panel.isFilePaneVisible)

        press(action, panel, state = true)
        assertTrue("The file pane did not come back", panel.isFilePaneVisible)
    }

    /** Neither button pressed is the third state: no diff pane at all. */
    fun `test neither diff button is pressed with no diff pane`() {
        val panel = newPanel()
        panel.isDiffPreviewVisible = false

        assertFalse("The right-hand button is pressed", isPressed(GitReflogPreviewOnTheRightAction(), panel))
        assertFalse("The bottom button is pressed", isPressed(GitReflogPreviewAtTheBottomAction(), panel))
    }

    /** One pressed at a time, and it is the one standing for the side the pane is on. */
    fun `test the pressed diff button is the one for the side the pane is on`() {
        val panel = newPanel()
        panel.isDiffPreviewVisible = true

        panel.isDiffPreviewAtBottom = true
        assertTrue("The bottom button is not pressed", isPressed(GitReflogPreviewAtTheBottomAction(), panel))
        assertFalse("Both diff buttons are pressed at once", isPressed(GitReflogPreviewOnTheRightAction(), panel))

        panel.isDiffPreviewAtBottom = false
        assertTrue("The right-hand button is not pressed", isPressed(GitReflogPreviewOnTheRightAction(), panel))
        assertFalse("Both diff buttons are pressed at once", isPressed(GitReflogPreviewAtTheBottomAction(), panel))
    }

    /** Pressing the button that is already down is how the diff pane is put away, there being no third button. */
    fun `test pressing the diff button that is already down puts the pane away`() {
        val panel = newPanel()
        val bottom = GitReflogPreviewAtTheBottomAction()

        press(bottom, panel, state = true)
        assertTrue(panel.isDiffPreviewVisible)

        press(bottom, panel, state = false)
        assertFalse("The diff pane stayed up", panel.isDiffPreviewVisible)
    }

    /**
     * Asking for the other side is how the pane is moved as well as shown, whether it was up at the time or not.
     *
     * What this does not cover is the order the two are set in. The action sets the side before showing the pane
     * so that one being brought back does not open where it was left and jump - but both orders end at the same
     * state, the difference being a frame of the pane in the wrong place, so no assertion on the state can see it.
     */
    fun `test a diff pane asked for on the other side ends up there`() {
        val panel = newPanel()

        // Moved while it is up.
        press(GitReflogPreviewAtTheBottomAction(), panel, state = true)
        press(GitReflogPreviewOnTheRightAction(), panel, state = true)

        assertTrue("The diff pane is not up", panel.isDiffPreviewVisible)
        assertFalse("The diff pane is not on the side it was asked for", panel.isDiffPreviewAtBottom)

        // Brought back on the other side after being put away.
        press(GitReflogPreviewOnTheRightAction(), panel, state = false)
        press(GitReflogPreviewAtTheBottomAction(), panel, state = true)

        assertTrue("The diff pane did not come back", panel.isDiffPreviewVisible)
        assertTrue("The diff pane came back where it was left, not where it was asked for", panel.isDiffPreviewAtBottom)
    }

    /** Away from the tab there is no layout to change. */
    fun `test the buttons are not offered away from the tab`() {
        assertFalse(
            "The file pane button is offered with no tab in the context",
            updated(GitReflogShowFilesAction(), SimpleDataContext.EMPTY_CONTEXT).isEnabled,
        )
        assertFalse(
            "A diff button is offered with no tab in the context",
            updated(GitReflogPreviewAtTheBottomAction(), SimpleDataContext.EMPTY_CONTEXT).isEnabled,
        )
    }

    private fun newPanel(): GitReflogPanel {
        val panel = GitReflogPanel(project)
        Disposer.register(testRootDisposable, panel)
        return panel
    }

    private fun isPressed(action: ToggleAction, panel: GitReflogPanel): Boolean =
        Toggleable.isSelected(updated(action, contextOf(panel)))

    /** Clicking the button, which is what a toolbar does to a toggle once the user has pressed it. */
    private fun press(action: ToggleAction, panel: GitReflogPanel, state: Boolean) = action.setSelected(
        AnActionEvent.createEvent(
            contextOf(panel),
            Presentation(),
            ActionPlaces.UNKNOWN,
            ActionUiKind.TOOLBAR,
            null,
        ),
        state,
    )

    private fun contextOf(panel: GitReflogPanel): DataContext =
        SimpleDataContext.getSimpleContext(GitReflogDataKeys.PANEL, panel)
}
