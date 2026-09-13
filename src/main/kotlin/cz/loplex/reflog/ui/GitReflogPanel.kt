package cz.loplex.reflog.ui

import com.intellij.dvcs.DvcsUtil
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
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.TableUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogData
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogReader
import cz.loplex.reflog.GitReflogRef
import cz.loplex.reflog.GitReflogService
import cz.loplex.reflog.actions.showReflogEntryDiff
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Job
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Content of the Reflog tab: the reflog of one ref of one repository, plus the toolbar that picks what is read
 * and the filters that narrow what of it is shown.
 */
internal class GitReflogPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val tableModel = GitReflogTableModel()
    private val table = TableView(tableModel)
    private val searchField = SearchTextField(false)
    private val countLabel = JBLabel()
    private val repositoryFilter = RepositoryFilter()
    private val refFilter = RefFilter()
    private val actionKindFilter = ActionKindFilter()
    private val filters = listOf(repositoryFilter, refFilter, actionKindFilter)
    private var loadJob: Job? = null
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

    /** Everything the last read returned; the table shows what passes [filter]. */
    private var entries: List<GitReflogEntry> = emptyList()
    private var filter = GitReflogFilter()

    /** Refs the current repository has a reflog for, as of the last read. */
    private var refs: List<GitReflogRef> = listOf(GitReflogRef.HEAD)

    /** How many records the next read asks git for; raised a page at a time by [loadMore]. */
    private var limit = GitReflogReader.PAGE_SIZE

    /** Repository whose reflog is currently shown. */
    var repository: GitRepository? = null
        private set

    /** Ref whose reflog is currently shown. */
    var ref: GitReflogRef = GitReflogRef.HEAD
        private set

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

        setContent(ScrollPaneFactory.createScrollPane(table, true))
        toolbar = createToolbar()
        installContextMenu()
        installDoubleClickHandler()

        subscribeToRepositoryChanges()
        selectRepository(GitBranchUtil.getCurrentRepository(project) ?: repositories().firstOrNull())
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
        countLabel.text = when {
            // Once the read hits its limit, the number of entries is a property of the limit, not of the reflog.
            hasMore -> GitReflogBundle.message("reflog.count.capped", shown.size, entries.size)
            shown.size != entries.size -> GitReflogBundle.message("reflog.count.filtered", shown.size, entries.size)
            else -> ""
        }

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
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(2))).apply {
            isOpaque = false
            add(countLabel)
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
    private fun actionKinds(): List<String> =
        entries.mapNotNullTo(sortedSetOf()) { it.actionKind.ifEmpty { null } }.toList()

    private fun setUpSearchField() {
        searchField.textEditor.columns = SEARCH_FIELD_COLUMNS
        searchField.textEditor.emptyText.text = GitReflogBundle.message("reflog.filter.text.hint")
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = setFilter(filter.copy(text = searchField.text))
        })
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
                val entry = table.selectedObjects.singleOrNull() ?: return false
                showReflogEntryDiff(project, repository, entry)
                return true
            }
        }.installOn(table)
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

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)

        val selected = table.selectedObjects
        sink[CommonDataKeys.PROJECT] = project
        sink[GitReflogDataKeys.PANEL] = this
        sink[GitReflogDataKeys.REPOSITORY] = repository
        sink[GitReflogDataKeys.SELECTED_ENTRIES] = selected
        sink[VcsDataKeys.VCS] = GitVcs.getKey()
        sink[VcsDataKeys.VCS_REVISION_NUMBER] = selected.firstOrNull()?.revisionNumber
        sink[VcsDataKeys.VCS_REVISION_NUMBERS] = selected.map { it.revisionNumber }.toTypedArray()
    }

    override fun dispose() {
        disposed = true
        loadJob?.cancel()
    }

    /**
     * Shows which repository the tab reads, and lets it be switched. Hidden unless the project holds more than
     * one, and never marked as a set filter: it narrows nothing, it only says what is being looked at.
     */
    private inner class RepositoryFilter :
        GitReflogFilterComponent(GitReflogBundle.lazyMessage("reflog.filter.repository.name")) {

        override fun getCurrentText(): String =
            repository?.let { DvcsUtil.getShortRepositoryName(it) } ?: emptyFilterValue

        override fun getEmptyFilterValue(): String = GitReflogBundle.message("reflog.filter.repository.none")

        override fun isValueSelected(): Boolean = false

        override fun createResetAction(): Runnable = Runnable { }

        override fun createActionGroup(): ActionGroup = DefaultActionGroup(
            repositories().map { repository ->
                DumbAwareAction.create(DvcsUtil.getShortRepositoryName(repository)) { selectRepository(repository) }
            },
        )
    }

    /** Lets the user pick any ref the repository holds a reflog for, grouped by what kind of ref it is. */
    private inner class RefFilter : GitReflogFilterComponent(GitReflogBundle.lazyMessage("reflog.filter.ref.name")) {

        override fun getCurrentText(): String = ref.presentableName

        override fun getEmptyFilterValue(): String = GitReflogRef.HEAD.presentableName

        /** HEAD is where the tab starts and what it falls back to, so it reads as the unset value. */
        override fun isValueSelected(): Boolean = ref != GitReflogRef.HEAD

        override fun createResetAction(): Runnable = Runnable { selectRef(GitReflogRef.HEAD) }

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
     */
    private inner class ActionKindFilter :
        GitReflogFilterComponent(GitReflogBundle.lazyMessage("reflog.filter.action.name")) {

        override fun getCurrentText(): String {
            val chosen = filter.actionKinds
            return when {
                chosen.isEmpty() -> emptyFilterValue
                chosen.size == 1 -> chosen.first()
                else -> GitReflogBundle.message("reflog.filter.action.several", chosen.size)
            }
        }

        override fun getEmptyFilterValue(): String = GitReflogBundle.message("reflog.filter.action.all")

        override fun isValueSelected(): Boolean = filter.actionKinds.isNotEmpty()

        override fun createResetAction(): Runnable = Runnable { setFilter(filter.copy(actionKinds = emptySet())) }

        override fun createActionGroup(): ActionGroup {
            val group = DefaultActionGroup()
            group.add(DumbAwareAction.create(GitReflogBundle.message("reflog.filter.action.all")) {
                setFilter(filter.copy(actionKinds = emptySet()))
            })
            group.addSeparator()
            actionKinds().forEach { kind -> group.add(ActionKindToggle(kind)) }
            return group
        }
    }

    /** One checkbox of the action kind popup; the popup stays open so that several kinds can be picked at once. */
    private inner class ActionKindToggle(private val kind: String) : ToggleAction(kind), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Always
        }

        override fun isSelected(e: AnActionEvent): Boolean = kind in filter.actionKinds

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            val kinds = filter.actionKinds.toMutableSet()
            if (state) kinds.add(kind) else kinds.remove(kind)
            setFilter(filter.copy(actionKinds = kinds))
        }
    }

    companion object {
        const val TAB_NAME: String = "Reflog"
        private const val TOOLBAR_PLACE = "GitReflogToolbar"
        private const val TOOLBAR_GROUP_ID = "GitReflog.Toolbar"
        private const val CONTEXT_MENU_PLACE = "GitReflogPopup"
        private const val CONTEXT_MENU_GROUP_ID = "GitReflog.ContextMenu"
        private const val SEARCH_FIELD_COLUMNS = 16
        private const val REPOSITORY_CHANGE_DELAY_MS = 300
    }
}
