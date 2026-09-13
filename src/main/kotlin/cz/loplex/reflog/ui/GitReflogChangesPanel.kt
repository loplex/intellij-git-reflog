package cz.loplex.reflog.ui

import com.intellij.diff.impl.DiffEditorViewer
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
 * Laid out the way the Log lays its own out - the files beside the table, the diff across the bottom of both -
 * and it owns [mainComponent] for that reason: a diff is read across, so it takes the width of the whole tab
 * rather than the half a side-by-side split would leave it.
 */
internal class GitReflogChangesPanel(project: Project, mainComponent: JComponent) : BorderLayoutPanel(), Disposable {

    private val browser = SimpleAsyncChangesBrowser(project, false, false)
    private val filesSplitter = OnePixelSplitter(false, FILES_SPLITTER_PROPORTION, 0.6f)
    private val diffSplitter = OnePixelSplitter(true, DIFF_SPLITTER_PROPORTION, 0.6f)

    /** Follows the file pane's selection on its own, through the tracker the platform installs on the tree. */
    private val diffViewer: DiffEditorViewer = TreeHandlerEditorDiffPreview.createDefaultViewer(
        browser.viewer,
        DefaultChangesTreeDiffPreviewHandler,
        DIFF_PLACE,
    )

    init {
        filesSplitter.firstComponent = mainComponent
        filesSplitter.secondComponent = browser
        diffSplitter.firstComponent = filesSplitter
        diffSplitter.secondComponent = diffViewer.component
        addToCenter(diffSplitter)
    }

    /** Shows [changes] as the files of the entry, or [emptyText] in their place when there are none. */
    fun setChanges(changes: List<Change>, emptyText: @Nls String) {
        browser.viewer.setEmptyText(emptyText)
        browser.setChangesToDisplay(changes)
    }

    /** Empties the file pane and says why it is empty. */
    fun showEmptyText(text: @Nls String) = setChanges(emptyList(), text)

    override fun dispose() {
        Disposer.dispose(diffViewer.disposable)
        browser.shutdown()
        diffSplitter.dispose()
        filesSplitter.dispose()
    }

    private companion object {
        /**
         * Place of the diff viewer. A place of its own rather than the Log's, so that the settings the diff
         * toolbar writes - ignore whitespace, the viewer to use - belong to this tab instead of following the Log.
         */
        const val DIFF_PLACE = "GitReflogDiffPreview"
        const val FILES_SPLITTER_PROPORTION = "GitReflog.files.splitter.proportion"
        const val DIFF_SPLITTER_PROPORTION = "GitReflog.diff.splitter.proportion"
    }
}
