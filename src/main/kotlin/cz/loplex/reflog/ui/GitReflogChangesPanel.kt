package cz.loplex.reflog.ui

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.DefaultChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.components.BorderLayoutPanel
import cz.loplex.reflog.GitReflogBundle
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
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

    private val browser = GitReflogChangesBrowser(project)

    /**
     * The file pane under a loading indicator, the way the Log puts one over its own.
     *
     * Emptying the list to say that a read is under way costs the answer that is already on screen, and the read
     * runs on every step through the table - so the list blinked its way through a walk of the reflog, saying
     * least exactly when the eye was on it. The indicator leaves the previous list standing instead.
     *
     * [LOADING_DELAY_MS] is what keeps the cure from being the disease: a read that comes back inside it draws no
     * indicator at all, so stepping through entries stays as still as the list now does.
     */
    private val loadingPanel = JBLoadingPanel(BorderLayout(), this, LOADING_DELAY_MS).apply {
        add(browser, BorderLayout.CENTER)
        setLoadingText(GitReflogBundle.message("reflog.changes.loading"))
    }

    private val filesSplitter = OnePixelSplitter(false, FILES_SPLITTER_PROPORTION, 0.6f)
    private val diffSplitter = OnePixelSplitter(true, DIFF_SPLITTER_PROPORTION, 0.6f)

    /** Alive only while the diff pane is shown; recreated whenever it is switched back on. */
    private var diffViewer: DiffEditorViewer? = null

    /**
     * Whether the file pane is shown, remembered across sessions like the diff pane's own placement.
     *
     * The list of files and the diff of one of them answer different questions - which files an entry touched,
     * and what it did to one of them - so either is worth having without the other, and the table on its own is
     * worth having without both.
     */
    var isFilePaneVisible: Boolean
        get() = PropertiesComponent.getInstance().getBoolean(SHOW_FILE_PANE, true)
        set(value) {
            PropertiesComponent.getInstance().setValue(SHOW_FILE_PANE, value, true)
            updateFilePane()
        }

    /** Whether either pane is up, which is what makes reading an entry's changes worth doing at all. */
    val isAnyPaneVisible: Boolean get() = isFilePaneVisible || isDiffPreviewVisible

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
        // Dragged to the edge the file pane would otherwise close to nothing, leaving a handle where a pane was
        // and no way to tell what had happened to it. There is a button for putting it away, which says so.
        filesSplitter.setHonorComponentsMinimumSize(true)
        loadingPanel.minimumSize = Dimension(JBUI.scale(MIN_FILE_PANE_WIDTH), 0)
        mainComponent.minimumSize = Dimension(JBUI.scale(MIN_TABLE_WIDTH), 0)
        filesSplitter.firstComponent = mainComponent
        diffSplitter.firstComponent = filesSplitter
        addToCenter(diffSplitter)
        updateFilePane()
        updateDiffPreview()
    }

    /** Shows [changes] as the files of the entry, or [emptyText] in their place when there are none. */
    fun setChanges(changes: List<Change>, emptyText: @Nls String) {
        stopLoading()
        browser.viewer.setEmptyText(emptyText)
        browser.setChangesToDisplay(changes)
    }

    /** Empties the file pane and says why it is empty. */
    fun showEmptyText(text: @Nls String) = setChanges(emptyList(), text)

    /** Says that the changes are being read, leaving what is already on screen where it is. */
    fun startLoading() = loadingPanel.startLoading()

    /**
     * Takes the indicator down without putting anything in its place.
     *
     * For the reads that end without an answer to show - a selection moving on before the last one came back,
     * both panes being put away under a read - which leave nothing to call [setChanges] for.
     *
     * Called whether or not an indicator is up: inside [LOADING_DELAY_MS] there is none yet, only a request for
     * one, and this is what withdraws it. Guarding the call on an indicator being up would let that request
     * through, raising an indicator over a read that had already finished, with nothing left to take it down.
     */
    fun stopLoading() = loadingPanel.stopLoading()

    override fun dispose() {
        diffViewer?.let { Disposer.dispose(it.disposable) }
        diffViewer = null
        browser.shutdown()
        diffSplitter.dispose()
        filesSplitter.dispose()
    }

    /**
     * Brings the file pane in line with [isFilePaneVisible].
     *
     * Taken out of the splitter rather than merely hidden, so that the table is given the width back; the browser
     * itself stays alive either way, being what the diff pane follows the selection of.
     */
    private fun updateFilePane() {
        filesSplitter.secondComponent = if (isFilePaneVisible) loadingPanel else null
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

    companion object {
        /**
         * Where the placement of the two panes is kept between sessions. Not private, so that a test can put back
         * what a run of it leaves in the application's own properties.
         */
        const val SHOW_FILE_PANE: String = "GitReflog.showFilePane"
        const val SHOW_DIFF_PREVIEW: String = "GitReflog.showDiffPreview"
        const val DIFF_PREVIEW_AT_BOTTOM: String = "GitReflog.diffPreviewAtBottom"

        /**
         * Place of the diff viewer. A place of its own rather than the Log's, so that the settings the diff
         * toolbar writes - ignore whitespace, the viewer to use - belong to this tab instead of following the Log.
         */
        const val DIFF_PLACE = "GitReflogDiffPreview"
        /** Narrowest the file pane may be dragged: its own toolbar, and room for a file name beside it. */
        const val MIN_FILE_PANE_WIDTH = 180

        /** Narrowest the table may be dragged, which is a couple of its columns. */
        const val MIN_TABLE_WIDTH = 240

        const val FILES_SPLITTER_PROPORTION = "GitReflog.files.splitter.proportion"
        const val DIFF_SPLITTER_PROPORTION = "GitReflog.diff.splitter.proportion"

        /**
         * How long a read may take before it is worth saying that it is under way.
         *
         * A second, which is the length at which a wait stops reading as an answer and starts reading as a delay
         * - the threshold Nielsen's response-time work puts on an operation feeling immediate, and the reason an
         * indicator below it costs more than it gives.
         *
         * In practice no ordinary read comes near it: reading the changes of an entry takes some 40-80 ms, so
         * this indicator is never seen at all on a repository of any usual size. That is the intent rather than
         * a waste - it is here for the read that has genuinely gone slow, and the list staying put underneath is
         * what the panel is for the rest of the time.
         */
        const val LOADING_DELAY_MS = 1000
    }
}
