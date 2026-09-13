package cz.loplex.reflog.ui

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.DefaultChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesBrowser
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * The two panes the Log has next to its table: the files the selected entry touched, and the diff of the file
 * selected among them.
 *
 * Laid out the way the Log lays its own out - the files beside the table, the diff wrapping both - and it owns
 * [mainComponent] for that reason: the diff pane is the wider of the two, so it goes around the table rather
 * than beside it.
 */
internal class GitReflogChangesPanel(project: Project, mainComponent: JComponent) : BorderLayoutPanel(), Disposable {

    private val browser = SimpleAsyncChangesBrowser(project, false, false)
    private val filesSplitter = OnePixelSplitter(false, FILES_SPLITTER_PROPORTION, 0.6f)
    private val diffSplitter = OnePixelSplitter(true, DIFF_SPLITTER_PROPORTION, 0.6f)

    /** Alive only while the diff pane is shown; recreated whenever it is switched back on. */
    private var diffViewer: DiffEditorViewer? = null

    /**
     * Whether the diff pane is shown, remembered across sessions. Kept outside the project so that the tab opens
     * the way the user last left it in any project, which is how the Log remembers its own preview.
     */
    var isDiffPreviewVisible: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(SHOW_DIFF_PREVIEW, true)
        set(value) {
            PropertiesComponent.getInstance().setValue(SHOW_DIFF_PREVIEW, value, true)
            updateDiffPreview()
        }

    /**
     * Whether the diff pane spans the bottom of the tab rather than running down its right-hand side.
     *
     * The bottom is where it starts, and where the Log starts it too: a diff is read across, so the width of the
     * whole tab suits it better than the half a side-by-side split would leave it.
     */
    var isDiffPreviewAtBottom: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(DIFF_PREVIEW_AT_BOTTOM, true)
        set(value) {
            PropertiesComponent.getInstance().setValue(DIFF_PREVIEW_AT_BOTTOM, value, true)
            diffSplitter.orientation = value
        }

    init {
        diffSplitter.orientation = isDiffPreviewAtBottom
        filesSplitter.firstComponent = mainComponent
        filesSplitter.secondComponent = browser
        diffSplitter.firstComponent = filesSplitter
        addToCenter(diffSplitter)
        updateDiffPreview()
    }

    /** Shows [changes] as the files of the entry, or [emptyText] in their place when there are none. */
    fun setChanges(changes: List<Change>, emptyText: @Nls String) {
        browser.viewer.setEmptyText(emptyText)
        browser.setChangesToDisplay(changes)
    }

    /** Empties the file pane and says why it is empty. */
    fun showEmptyText(text: @Nls String) = setChanges(emptyList(), text)

    override fun dispose() {
        diffViewer?.let { Disposer.dispose(it.disposable) }
        diffViewer = null
        browser.shutdown()
        diffSplitter.dispose()
        filesSplitter.dispose()
    }

    /**
     * Brings the diff pane in line with [isDiffPreviewVisible], creating the viewer when it is switched on and
     * disposing it when it is switched off.
     *
     * The viewer is not merely hidden: it follows the file pane's selection and loads the file contents behind
     * every move of it, which is work that a hidden pane has no reason to be doing.
     */
    private fun updateDiffPreview() {
        val shouldBeShown = isDiffPreviewVisible
        if (shouldBeShown == (diffViewer != null)) return

        diffViewer?.let {
            diffSplitter.secondComponent = null
            Disposer.dispose(it.disposable)
        }
        diffViewer = null

        if (shouldBeShown) {
            val viewer = TreeHandlerEditorDiffPreview.createDefaultViewer(
                browser.viewer,
                DefaultChangesTreeDiffPreviewHandler,
                DIFF_PLACE,
            )
            diffSplitter.secondComponent = viewer.component
            diffViewer = viewer
        }
    }

    private companion object {
        /**
         * Place of the diff viewer. A place of its own rather than the Log's, so that the settings the diff
         * toolbar writes - ignore whitespace, the viewer to use - belong to this tab instead of following the Log.
         */
        const val DIFF_PLACE = "GitReflogDiffPreview"
        const val FILES_SPLITTER_PROPORTION = "GitReflog.files.splitter.proportion"
        const val DIFF_SPLITTER_PROPORTION = "GitReflog.diff.splitter.proportion"
        const val SHOW_DIFF_PREVIEW = "GitReflog.showDiffPreview"
        const val DIFF_PREVIEW_AT_BOTTOM = "GitReflog.diffPreviewAtBottom"
    }
}
