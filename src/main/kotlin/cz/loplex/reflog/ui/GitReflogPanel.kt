package cz.loplex.reflog.ui

import com.intellij.dvcs.DvcsUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.TableUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import cz.loplex.reflog.GitReflogAncestry
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogChangesResult
import cz.loplex.reflog.GitReflogData
import cz.loplex.reflog.GitReflogDiffMode
import cz.loplex.reflog.GitReflogDiffModes
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogReader
import cz.loplex.reflog.GitReflogRef
import cz.loplex.reflog.GitReflogSelection
import cz.loplex.reflog.GitReflogService
import cz.loplex.reflog.actions.showReflogDiff
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Job
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent

/**
 * Content of the Reflog tab: the reflog of one ref of one repository, plus the toolbar that picks what is read
 * and the filters that narrow what of it is shown.
 */
/**
 * What the label beside Load More says about how much of the reflog is on screen.
 *
 * The case worth naming is the last one: with the whole reflog read and nothing filtered out, the label used to
 * say nothing at all. Load More then took both itself and the count away on the click that finished the reading,
 * leaving the row emptier than before and no answer to whether anything had happened. Saying that this is all of
 * it puts the answer where the button had been.
 */
internal fun countTextFor(shown: Int, total: Int, hasMore: Boolean): String = when {
    // Nothing read is said by the table itself, which stands empty with its own words in it.
    total == 0 -> ""
    // Once the read hits its limit, the number of entries is a property of the limit, not of the reflog.
    hasMore -> GitReflogBundle.message("reflog.count.capped", shown, total)
    shown != total -> GitReflogBundle.message("reflog.count.filtered", shown, total)
    else -> GitReflogBundle.message("reflog.count.all", total)
}

internal class GitReflogPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val tableModel = GitReflogTableModel()
    private val table = TableView(tableModel)
    private val changesPanel = GitReflogChangesPanel(project, ScrollPaneFactory.createScrollPane(table, true))
    /**
     * Kept across sessions under a name of its own, the way every search field in the IDE keeps its own: what was
     * looked for in a reflog is worth offering again, a reflog being where one goes back to look for the same
     * lost commit twice.
     */
    private val searchField = SearchTextField(true, SEARCH_HISTORY)
    private val countLabel = JBLabel()

    /**
     * Reads another page of older records.
     *
     * A link beside the count rather than a button on the toolbar. What it says is about what has been read, not
     * about how the tab is laid out, so it belongs with the number it moves - and being drawn by this panel it
     * appears the moment a read comes back, where an action waits to be asked by a toolbar that has no way of
     * knowing that git answered.
     *
     * Hidden unless a read came back full, which is the only case in which there may be more to read at all. The
     * filters cannot help there: they run over the entries already read.
     */
    private val loadMoreLink = ActionLink(GitReflogBundle.message("reflog.load.more")) { loadMore() }.apply {
        toolTipText = GitReflogBundle.message("reflog.load.more.tooltip")
        // Carried here rather than by the row, so that the space in front of it is hidden along with it.
        border = JBUI.Borders.emptyLeft(LINK_GAP_BEFORE)
        isVisible = false
    }
    private val repositoryFilter = RepositoryFilter()
    private val refFilter = RefFilter()
    private val actionKindFilter = ActionKindFilter()
    private val filters = listOf(repositoryFilter, refFilter, actionKindFilter)
    private var loadJob: Job? = null
    private var changesJob: Job? = null
    private var disposed = false

    /**
     * Collapses a burst of repository changes into a single read. An interactive rebase publishes
     * [GitRepository.GIT_REPO_CHANGE] once per step, and each read costs a `rev-parse`, a walk of the log
     * directory and a `git reflog show`.
     *
     * Requests are throttled rather than postponed: the first change of a burst schedules the read and the rest
     * fold into it, so a long-running rebase still refreshes while it runs instead of only at its end.
     */
    private val reloadAlarm = SingleAlarm.singleEdtAlarm(REPOSITORY_CHANGE_DELAY_MS, this, Runnable { reload() })

    /**
     * Holds the changes pane back while the selection is still moving. Every entry costs a `git show`, and
     * walking the table with the arrow keys would otherwise start one per row passed over.
     *
     * Postponed rather than throttled, unlike [reloadAlarm]: what matters here is the row the user stops on, not
     * the one they started from.
     */
    private val changesAlarm = SingleAlarm.singleEdtAlarm(SELECTION_CHANGE_DELAY_MS, this, Runnable { loadChanges() })

    /** Everything the last read returned; the table shows what passes [filter]. */
    private var entries: List<GitReflogEntry> = emptyList()
    private var filter = GitReflogFilter()

    /** Refs the current repository has a reflog for, as of the last read. */
    private var refs: List<GitReflogRef> = listOf(GitReflogRef.HEAD)

    /** How many records the next read asks git for; raised a page at a time by [loadMore]. */
    private var limit = GitReflogReader.PAGE_SIZE

    /**
     * Selection and mode the file pane currently shows, if any.
     *
     * What a commit changed cannot change, so a selection that comes back to the same entries under the same
     * mode - which is what every auto-refresh does, by restoring the selection it had - is left alone instead of
     * being re-read and redrawn under the user.
     */
    private var shownChangesFor: ChangesKey? = null

    /**
     * What the last read found out about how the selected entries stand to one another in the graph.
     *
     * Reset to [GitReflogAncestry.UNKNOWN] with every new selection, so that a mode is offered until git says it
     * should not be rather than the other way round: the answer takes a git call, and blanking the modes until
     * it lands would make the switch flicker on every arrow key.
     */
    private var ancestry: GitReflogAncestry = GitReflogAncestry.UNKNOWN

    /** Repository whose reflog is currently shown. */
    var repository: GitRepository? = null
        private set

    /** Ref whose reflog is currently shown. */
    var ref: GitReflogRef = GitReflogRef.HEAD
        private set

    /**
     * Which question the file pane answers about the selected entries, remembered across sessions the way the
     * other choices about the pane are.
     *
     * What is remembered is what the user picked, not what was shown: a mode that does not fit the selection of
     * the moment gives way to one that does, and comes back as soon as a selection it suits is made again.
     */
    var diffMode: GitReflogDiffMode
        get() = PropertiesComponent.getInstance().getValue(DIFF_MODE)
            ?.let { name -> GitReflogDiffMode.entries.firstOrNull { it.name == name } }
            ?: GitReflogDiffMode.DEFAULT
        set(value) {
            PropertiesComponent.getInstance().setValue(DIFF_MODE, value.name)
            loadChanges()
        }


    /** Whether the list of files the selected entry changed is shown at all. */
    var isFilePaneVisible: Boolean
        get() = changesPanel.isFilePaneVisible
        set(value) {
            changesPanel.isFilePaneVisible = value
            reloadChanges()
        }

    /** Whether the diff of the file selected in the file pane is shown at all. */
    var isDiffPreviewVisible: Boolean
        get() = changesPanel.isDiffPreviewVisible
        set(value) {
            changesPanel.isDiffPreviewVisible = value
            reloadChanges()
        }

    /** Whether that diff spans the bottom of the tab rather than its right-hand side. */
    var isDiffPreviewAtBottom: Boolean
        get() = changesPanel.isDiffPreviewAtBottom
        set(value) {
            changesPanel.isDiffPreviewAtBottom = value
        }

    /**
     * Whether older records exist that have not been read. A read that came back with exactly as many entries as
     * it asked for has more behind it - git stops at the count, not at the end of the reflog.
     */
    val hasMore: Boolean get() = entries.size >= limit

    init {
        table.setShowGrid(false)
        table.tableHeader.reorderingAllowed = false
        tableModel.applyColumnWidths(table)
        setUpSearchField()

        Disposer.register(this, changesPanel)
        setContent(changesPanel)
        toolbar = createToolbar()
        installContextMenu()
        installDoubleClickHandler()
        installSelectionHandler()
        changesPanel.showEmptyText(GitReflogBundle.message("reflog.changes.none.selected"))

        subscribeToRepositoryChanges()
        subscribeToWorkingTreeChanges()
        // The repository a widget would show: the one holding the file being looked at, or, failing that, the one
        // git was last used on. Which is the right guess for a tab as well - it is opened on the work in hand.
        selectRepository(
            GitBranchUtil.guessWidgetRepository(project, DvcsUtil.getSelectedFile(project))
                ?: repositories().firstOrNull(),
        )
    }

    /**
     * Switches the tab to [repository] and reads its reflog.
     *
     * The ref falls back to `HEAD`, the only one every repository is guaranteed to have - branch names do not
     * carry over from the repository that was shown before.
     */
    fun selectRepository(repository: GitRepository?) {
        this.repository = repository
        this.ref = GitReflogRef.HEAD
        this.refs = listOf(GitReflogRef.HEAD)
        this.limit = GitReflogReader.PAGE_SIZE
        reload()
    }

    /** Switches the tab to the reflog of [ref] in the current repository. */
    fun selectRef(ref: GitReflogRef) {
        this.ref = ref
        // Another reflog is another length; how far the previous one had been read says nothing about this one.
        this.limit = GitReflogReader.PAGE_SIZE
        reload()
    }

    /** Reads one more page of older records on top of what is already shown. */
    fun loadMore() {
        limit += GitReflogReader.PAGE_SIZE
        reload()
    }

    /** Re-reads the reflog of the selected repository, dropping a read that is still running. */
    fun reload() {
        loadJob?.cancel()
        updateFilters()

        val repository = repository
        if (repository == null) {
            showEmptyText(GitReflogBundle.message("reflog.status.no.repository"))
            return
        }

        loadJob = GitReflogService.getInstance(project).loadReflog(
            repository,
            ref,
            limit,
            // Entries already on screen stay until the new ones arrive; the text is only seen on the first read.
            onStarted = { table.emptyText.text = GitReflogBundle.message("reflog.status.loading") },
            onFinished = { result -> show(result) },
        )
    }

    private fun show(result: Result<GitReflogData>) {
        result
            .onSuccess { data ->
                refs = data.refs
                // The read falls back to HEAD when the ref asked for turns out to be gone.
                ref = data.ref
                entries = data.entries
                applyFilter()
            }
            .onFailure { error ->
                showEmptyText(GitReflogBundle.message("reflog.status.error", error.message.orEmpty()))
            }
    }

    private fun showEmptyText(text: String) {
        entries = emptyList()
        tableModel.items = ArrayList()
        table.emptyText.text = text
        countLabel.text = ""
        // Nothing was read, so there is nothing to read more of - a repository with no reflog, or one that could
        // not be read at all.
        loadMoreLink.isVisible = false
        updateFilters()
    }

    private fun setFilter(filter: GitReflogFilter) {
        this.filter = filter
        applyFilter()
    }

    private fun applyFilter() {
        val shown = entries.filter(filter::matches)
        // Replacing the items clears the selection, so what was selected is carried over by hand: an auto-refresh
        // fires after every commit, checkout and rebase, and it must not move the cursor out from under the user.
        val selected = table.selectedObjects.mapTo(HashSet()) { it.identity }
        tableModel.items = ArrayList(shown)
        restoreSelection(selected)

        table.emptyText.text = when {
            entries.isEmpty() -> GitReflogBundle.message("reflog.status.empty")
            else -> GitReflogBundle.message("reflog.status.no.match")
        }
        countLabel.text = countTextFor(shown.size, entries.size, hasMore)
        loadMoreLink.isVisible = hasMore

        updateFilters()
    }

    private fun restoreSelection(identities: Set<Any>) {
        if (identities.isEmpty()) return

        val rows = tableModel.items.withIndex().filter { it.value.identity in identities }.map { it.index }
        if (rows.isEmpty()) return

        TableUtil.selectRows(table, rows.toIntArray())
        TableUtil.scrollSelectionToVisible(table)
    }

    private fun repositories(): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories

    /**
     * Laid out the way the Log lays its own out: the text field first, the filters after it, and the actions at
     * the far end of the row.
     */
    private fun createToolbar(): JComponent {
        val actions = ActionManager.getInstance().createActionToolbar(
            TOOLBAR_PLACE,
            DefaultActionGroup(ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID)),
            true,
        )
        actions.targetComponent = this

        val left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            isOpaque = false
            add(searchField)
            filters.forEach { add(it.initUi()) }
        }
        // No gap of the row's own: the toolbar at the end of it brings an inset already, so a gap shared out
        // evenly leaves the separator further from the icons than from the count. Each piece carries the space it
        // wants on its left instead, which also means the space in front of Load More goes away with Load More.
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 0, JBUI.scale(2))).apply {
            isOpaque = false
            add(countLabel)
            add(loadMoreLink)
            // What has been read on one side, what to do with the tab on the other. The toolbar drew this line
            // itself while Load More was an action on it; drawn here it also stands when Load More is hidden,
            // the count on its left being reason enough to keep the two halves of the row apart.
            //
            // Given a height outright, because a row laid out by its contents has none to lend: the platform's
            // own SeparatorComponent asks for a height of zero when stood on end, on the expectation of being
            // stretched by whatever holds it, and is drawn as a gap where nothing stretches it.
            // A strut rather than a border on the separator: the separator is given its size outright, for want
            // of a row that would stretch it, and a size given outright leaves no room for a border to be added
            // to. Standing on its own the gap also holds when Load More is hidden.
            add(Box.createHorizontalStrut(JBUI.scale(SEPARATOR_GAP_BEFORE)))
            add(
                JSeparator(SwingConstants.VERTICAL).apply {
                    preferredSize = Dimension(preferredSize.width, JBUI.scale(SEPARATOR_HEIGHT))
                },
            )
            add(Box.createHorizontalStrut(JBUI.scale(SEPARATOR_GAP_AFTER)))
            add(actions.component)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    /**
     * Brings the filter components up to date with what the tab now holds.
     *
     * They draw themselves out of the panel's state rather than keeping a copy of it, so every change of that
     * state has to tell them to redraw.
     */
    private fun updateFilters() {
        repositoryFilter.isVisible = repositories().size > 1
        refFilter.isEnabled = repository != null
        actionKindFilter.isEnabled = actionKinds().isNotEmpty()
        filters.forEach { it.filterChanged() }
    }

    /**
     * Kinds present in the entries at hand. Stash entries carry no action at all, which is why the empty kind is
     * dropped rather than offered as a nameless item.
     */
    /**
     * Narrows the table to the kinds not in [excluded], taking "none of them" as "all of them".
     *
     * Excluding every kind there is asks to see nothing, which is no question at all, and it is reachable two
     * ways - unticking the last kind, and unticking All. Both come back to All instead, as an empty selection
     * does in the Log's own filters.
     */
    private fun setActionKindFilter(excluded: Set<String>) {
        val nothingLeft = excluded.containsAll(actionKinds())
        setFilter(filter.copy(excludedActionKinds = if (nothingLeft) emptySet() else excluded))
    }

    private fun actionKinds(): List<String> =
        entries.mapNotNullTo(sortedSetOf()) { it.actionKind.ifEmpty { null } }.toList()

    private fun setUpSearchField() {
        searchField.textEditor.columns = SEARCH_FIELD_COLUMNS
        searchField.textEditor.emptyText.text = GitReflogBundle.message("reflog.filter.text.hint")
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = setFilter(filter.copy(text = searchField.text))
        })
        // The table narrows as the text is typed, so Enter has nothing left to apply - which leaves it free to
        // mean what it means in the platform's own search fields: remember this one.
        searchField.textEditor.addActionListener { searchField.addCurrentTextToHistory() }
        countLabel.foreground = UIUtil.getContextHelpForeground()
    }

    private fun installContextMenu() {
        val group = ActionManager.getInstance().getAction(CONTEXT_MENU_GROUP_ID) as ActionGroup
        PopupHandler.installPopupMenu(table, group, CONTEXT_MENU_PLACE)
    }

    /** Opens the changes of the double-clicked entry, the way the Log tab opens a commit. */
    private fun installDoubleClickHandler() {
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                if (table.rowAtPoint(event.point) < 0) return false
                val repository = repository ?: return false
                // What the pane is showing, which is what Show Diff opens too - a double click on an entry is
                // the same gesture said faster, and the two must not answer differently.
                val mode = diffModes().effective ?: return false
                showReflogDiff(project, repository, selection(), mode)
                return true
            }
        }.installOn(table)
    }

    /** Keeps the file pane on the entry the table is standing on, the way the Log follows its own graph. */
    private fun installSelectionHandler() {
        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) changesAlarm.cancelAndRequest()
        }
    }

    /** What the table has selected, against the reflog it was selected from. */
    fun selection(): GitReflogSelection = GitReflogSelection(ref, entries, table.selectedObjects)

    /** What the modes stand at for what is selected right now, for the switch and the context menu to show. */
    fun diffModes(): GitReflogDiffModes = GitReflogDiffModes.of(diffMode, selection(), ancestry)

    /**
     * Reads the changes of the selected entries into the file pane, under the mode that fits them.
     *
     * Which mode that is settles in the background rather than here: telling [GitReflogDiffMode.UNION] apart
     * from a selection it must not be offered for takes a walk of the graph.
     */
    private fun loadChanges() {
        val repository = repository
        val selection = selection()

        if (repository == null || selection.selected.isEmpty()) {
            showChangesEmptyText(GitReflogBundle.message("reflog.changes.none.selected"))
            return
        }

        // What a commit changed cannot change, but what the working tree holds can, so that one reading is read
        // again every time the panel is asked to - after a refresh among other things, which is what follows the
        // commits and checkouts that move the tree under it.
        // With both panes away there is nobody for the read to answer, and the selection still moves.
        if (!changesPanel.isAnyPaneVisible) {
            changesJob?.cancel()
            changesPanel.stopLoading()
            shownChangesFor = null
            return
        }

        val key = ChangesKey(selection.selected.map { it.identity }, diffMode)
        if (key == shownChangesFor && diffMode != GitReflogDiffMode.WORKING_TREE) return

        changesJob?.cancel()
        shownChangesFor = key
        ancestry = GitReflogAncestry.UNKNOWN
        changesJob = GitReflogService.getInstance(project).loadChanges(
            repository,
            selection,
            diffMode,
            onStarted = { changesPanel.startLoading() },
            onFinished = { result -> showChanges(selection, result) },
        )
    }

    private fun showChanges(selection: GitReflogSelection, result: Result<GitReflogChangesResult>) {
        result
            .onSuccess { outcome ->
                ancestry = outcome.ancestry
                val mode = outcome.mode
                // Nothing fits a selection with nothing in it, which is what the pane says in its own words.
                if (mode == null) changesPanel.showEmptyText(GitReflogBundle.message("reflog.changes.none.selected"))
                else changesPanel.setChanges(outcome.changes, emptyTextFor(selection, mode))
            }
            .onFailure { error ->
                shownChangesFor = null
                changesPanel.showEmptyText(
                    GitReflogBundle.message("reflog.changes.error", error.message.orEmpty()),
                )
            }
    }

    /**
     * Says what came back empty, naming the two states the mode compared.
     *
     * Worth the three messages: "nothing changed" is a different statement about a reset than it is about a
     * commit, and a pane that does not say which comparison it made leaves the user unable to tell the mode
     * they asked for from the one they were given.
     */
    private fun emptyTextFor(selection: GitReflogSelection, mode: GitReflogDiffMode): String {
        val old = mode.oldSideOf(selection)
        val new = mode.newSideOf(selection)
        return when {
            mode == GitReflogDiffMode.WORKING_TREE && old != null ->
                GitReflogBundle.message("reflog.changes.empty.working.tree", old.shortHash)
            old != null && new != null ->
                GitReflogBundle.message("reflog.changes.empty.range", old.shortHash, new.shortHash)
            else ->
                GitReflogBundle.message("reflog.changes.empty", selection.newest?.shortHash.orEmpty())
        }
    }

    /** Reads the changes again for a pane that has just been brought back, having gone unread while it was away. */
    private fun reloadChanges() {
        shownChangesFor = null
        loadChanges()
    }

    private fun showChangesEmptyText(text: String) {
        changesJob?.cancel()
        shownChangesFor = null
        changesPanel.showEmptyText(text)
    }

    /**
     * The reflog is written by every commit, checkout, reset or rebase, and each of those also changes the state
     * of the repository - which makes [GitRepository.GIT_REPO_CHANGE] a good enough signal to keep the tab current.
     */
    private fun subscribeToRepositoryChanges() {
        val listener = GitRepositoryChangeListener { changed ->
            // The topic is published on a background thread; the panel state is only touched on the EDT.
            ApplicationManager.getApplication().invokeLater(
                { if (changed == repository) reloadAlarm.request() },
                ModalityState.any(),
                { disposed },
            )
        }
        project.messageBus.connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, listener)
    }

    /**
     * Comparing against the working tree is the one reading whose answer can change without the reflog changing,
     * and an edit that is only saved changes no git state for [subscribeToRepositoryChanges] to hear about.
     *
     * [ChangeListManager] is what does hear about it, and it has finished working out what changed by the time it
     * says so - which is the point at which the pane is worth reading again.
     */
    private fun subscribeToWorkingTreeChanges() {
        val listener = object : ChangeListListener {
            override fun changeListUpdateDone() {
                // Published off the EDT; the panel state is only touched on it.
                ApplicationManager.getApplication().invokeLater(
                    { if (isShowingWorkingTree()) changesAlarm.cancelAndRequest() },
                    ModalityState.any(),
                    { disposed },
                )
            }
        }
        project.messageBus.connect(this).subscribe(ChangeListListener.TOPIC, listener)
    }

    /**
     * Whether the file pane is showing the working tree, which is what makes a change to it worth reading.
     *
     * Asked of the mode rather than of the selection, so that the one rule saying when the working tree can be
     * compared at all stays in the one place that states it.
     */
    private fun isShowingWorkingTree(): Boolean =
        diffMode == GitReflogDiffMode.WORKING_TREE && diffMode.isApplicableTo(selection(), ancestry)

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)

        val selected = table.selectedObjects
        sink[CommonDataKeys.PROJECT] = project
        sink[GitReflogDataKeys.PANEL] = this
        sink[GitReflogDataKeys.REPOSITORY] = repository
        sink[GitReflogDataKeys.SELECTED_ENTRIES] = selected
        sink[GitReflogDataKeys.HAS_MORE] = hasMore
        sink[GitReflogDataKeys.DIFF_MODES] = diffModes()
        sink[GitReflogDataKeys.SELECTION] = selection()
        sink[VcsDataKeys.VCS] = GitVcs.getKey()
        sink[VcsDataKeys.VCS_REVISION_NUMBER] = selected.firstOrNull()?.revisionNumber
        sink[VcsDataKeys.VCS_REVISION_NUMBERS] = selected.map { it.revisionNumber }.toTypedArray()
    }

    override fun dispose() {
        disposed = true
        loadJob?.cancel()
        changesJob?.cancel()
    }

    /**
     * Shows which repository the tab reads, and lets it be switched. Hidden unless the project holds more than
     * one, and never marked as a set filter: it narrows nothing, it only says what is being looked at.
     */
    private inner class RepositoryFilter :
        GitReflogFilterComponent(GitReflogBundle.lazyMessage("reflog.filter.repository.name")) {

        override fun getCurrentText(): String = repository?.let { DvcsUtil.getShortRepositoryName(it) }.orEmpty()

        override fun createActionGroup(): ActionGroup = DefaultActionGroup(
            repositories().map { repository ->
                DumbAwareAction.create(DvcsUtil.getShortRepositoryName(repository)) { selectRepository(repository) }
            },
        )
    }

    /** Lets the user pick any ref the repository holds a reflog for, grouped by what kind of ref it is. */
    private inner class RefFilter :
        GitReflogFilterComponent(GitReflogBundle.lazyMessage("reflog.filter.ref.name")) {

        override fun getCurrentText(): String = ref.presentableName

        override fun createActionGroup(): ActionGroup {
            val group = DefaultActionGroup()
            var previousKind: GitReflogRef.Kind? = null
            refs.forEach { ref ->
                if (previousKind != null && ref.kind != previousKind) group.addSeparator(titleOf(ref.kind))
                group.add(DumbAwareAction.create(ref.presentableName) { selectRef(ref) })
                previousKind = ref.kind
            }
            return group
        }

        private fun titleOf(kind: GitReflogRef.Kind): String = when (kind) {
            GitReflogRef.Kind.HEAD -> GitReflogBundle.message("reflog.refs.head")
            GitReflogRef.Kind.LOCAL_BRANCH -> GitReflogBundle.message("reflog.refs.local")
            GitReflogRef.Kind.REMOTE_BRANCH -> GitReflogBundle.message("reflog.refs.remote")
            GitReflogRef.Kind.STASH -> GitReflogBundle.message("reflog.refs.stash")
            GitReflogRef.Kind.OTHER -> GitReflogBundle.message("reflog.refs.other")
        }
    }

    /**
     * Narrows the table to chosen kinds of operation. The kinds offered are the ones present in the entries at
     * hand, so the popup never lists an operation this reflog does not contain.
     *
     * Every kind starts out ticked, since every kind is shown; unticking one is what narrows the table. All at
     * the top ticks every kind, and unticking All unticks every one of them - the two ends of the same range,
     * which is what the item is there to reach in one step.
     */
    private inner class ActionKindFilter :
        GitReflogFilterComponent(GitReflogBundle.lazyMessage("reflog.filter.action.name")) {

        override fun getCurrentText(): String {
            if (filter.excludedActionKinds.isEmpty()) return GitReflogBundle.message("reflog.filter.action.all")

            // No "none" case: a filter that hides every kind is turned back into no filter at all.
            val shown = actionKinds() - filter.excludedActionKinds
            return if (shown.size == 1) shown.first()
            else GitReflogBundle.message("reflog.filter.action.several", shown.size)
        }

        override fun createActionGroup(): ActionGroup {
            val group = DefaultActionGroup()
            group.add(AllActionKindsToggle())
            group.addSeparator()
            actionKinds().forEach { kind -> group.add(ActionKindToggle(kind)) }
            return group
        }
    }

    /** Ticks every kind of operation at once, and unticks every one of them at once. */
    private inner class AllActionKindsToggle :
        ToggleAction(GitReflogBundle.message("reflog.filter.action.all")), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Always
        }

        override fun isSelected(e: AnActionEvent): Boolean = filter.excludedActionKinds.isEmpty()

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            setActionKindFilter(if (state) emptySet() else actionKinds().toSet())
        }
    }

    /** One checkbox of the action kind popup; the popup stays open so that several kinds can be picked at once. */
    private inner class ActionKindToggle(private val kind: String) : ToggleAction(kind), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Always
        }

        override fun isSelected(e: AnActionEvent): Boolean = kind !in filter.excludedActionKinds

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            val excluded = filter.excludedActionKinds.toMutableSet()
            if (state) excluded.remove(kind) else excluded.add(kind)
            setActionKindFilter(excluded)
        }
    }

    /** What a shown set of changes is keyed by: the entries it was read for, and the mode it was read under. */
    private data class ChangesKey(val entries: List<Any>, val mode: GitReflogDiffMode)

    companion object {
        const val TAB_NAME: String = "Reflog"
        private const val DIFF_MODE = "GitReflog.diffMode"
        private const val TOOLBAR_PLACE = "GitReflogToolbar"
        private const val TOOLBAR_GROUP_ID = "GitReflog.Toolbar"
        private const val CONTEXT_MENU_PLACE = "GitReflogPopup"
        private const val CONTEXT_MENU_GROUP_ID = "GitReflog.ContextMenu"
        private const val SEARCH_FIELD_COLUMNS = 16
        /** How tall the line between the two halves of the toolbar row stands, the row lending it none. */
        private const val SEPARATOR_HEIGHT = 16
        private const val LINK_GAP_BEFORE = 8
        private const val SEPARATOR_GAP_BEFORE = 8
        /** Smaller than the gap before it: the toolbar that follows brings an inset of its own. */
        private const val SEPARATOR_GAP_AFTER = 1
        /**
         * Where the field's history is kept between sessions. Not private, so that a test can put back what a
         * run of it leaves in the application's own properties.
         */
        const val SEARCH_HISTORY: String = "GitReflog.searchHistory"
        private const val REPOSITORY_CHANGE_DELAY_MS = 300
        private const val SELECTION_CHANGE_DELAY_MS = 150
    }
}
