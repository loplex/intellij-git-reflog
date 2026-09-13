package cz.loplex.reflog.ui

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogReader
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
    private var loadJob: Job? = null
    private var disposed = false

    /** Everything the last read returned; the table shows what passes [filter]. */
    private var entries: List<GitReflogEntry> = emptyList()
    private var filter = GitReflogFilter()

    /** Repository whose reflog is currently shown. */
    var repository: GitRepository? = null
        private set

    /** Ref whose reflog is currently shown: `HEAD` or a local branch. */
    var ref: String = GitReflogReader.HEAD_REF
        private set

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
        this.ref = GitReflogReader.HEAD_REF
        reload()
    }

    /** Switches the tab to the reflog of [ref] in the current repository. */
    fun selectRef(ref: String) {
        this.ref = ref
        reload()
    }

    /** Re-reads the reflog of the selected repository, dropping a read that is still running. */
    fun reload() {
        loadJob?.cancel()

        val repository = repository
        if (repository == null) {
            showEmptyText(GitReflogBundle.message("reflog.status.no.repository"))
            return
        }

        // A branch shown here can be deleted meanwhile, and git answers a reflog request for a gone ref with a
        // fatal error. HEAD is always there, so fall back to it rather than show that error.
        if (ref != GitReflogReader.HEAD_REF && repository.branches.localBranches.none { it.name == ref }) {
            ref = GitReflogReader.HEAD_REF
        }

        loadJob = GitReflogService.getInstance(project).loadReflog(
            repository,
            ref,
            // Entries already on screen stay until the new ones arrive; the text is only seen on the first read.
            onStarted = { table.emptyText.text = GitReflogBundle.message("reflog.status.loading") },
            onFinished = { result -> show(result) },
        )
    }

    private fun show(result: Result<List<GitReflogEntry>>) {
        result
            .onSuccess { loaded ->
                entries = loaded
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
    }

    private fun setFilter(filter: GitReflogFilter) {
        this.filter = filter
        applyFilter()
    }

    private fun applyFilter() {
        val shown = entries.filter(filter::matches)
        tableModel.items = ArrayList(shown)

        table.emptyText.text = when {
            entries.isEmpty() -> GitReflogBundle.message("reflog.status.empty")
            else -> GitReflogBundle.message("reflog.status.no.match")
        }
        countLabel.text = when {
            // Once the read hits its cap, the number of entries is a property of the cap, not of the reflog.
            entries.size == GitReflogReader.MAX_ENTRIES ->
                GitReflogBundle.message("reflog.count.capped", shown.size, entries.size)
            shown.size != entries.size -> GitReflogBundle.message("reflog.count.filtered", shown.size, entries.size)
            else -> ""
        }
    }

    private fun repositories(): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories

    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup(
            RepositorySelector(),
            RefSelector(),
            ActionKindFilter(),
            ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this

        val filters = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), JBUI.scale(2))).apply {
            isOpaque = false
            add(countLabel)
            add(searchField)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(toolbar.component, BorderLayout.WEST)
            add(filters, BorderLayout.EAST)
        }
    }

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
                { if (changed == repository) reload() },
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

    /** Lets the user pick the repository; hidden unless the project holds more than one. */
    private inner class RepositorySelector : ComboBoxAction(), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabledAndVisible = repositories().size > 1
            e.presentation.text = repository?.let { DvcsUtil.getShortRepositoryName(it) }
        }

        override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
            val group = DefaultActionGroup()
            repositories().forEach { repository ->
                group.add(DumbAwareAction.create(DvcsUtil.getShortRepositoryName(repository)) { selectRepository(repository) })
            }
            return group
        }
    }

    /** Lets the user pick the ref whose reflog is shown: `HEAD` or any local branch. */
    private inner class RefSelector : ComboBoxAction(), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = repository != null
            e.presentation.text = ref
        }

        override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
            val group = DefaultActionGroup()
            group.add(DumbAwareAction.create(GitReflogReader.HEAD_REF) { selectRef(GitReflogReader.HEAD_REF) })
            group.addSeparator()
            branchNames().forEach { branch ->
                group.add(DumbAwareAction.create(branch) { selectRef(branch) })
            }
            return group
        }

        private fun branchNames(): List<String> =
            repository?.branches?.localBranches.orEmpty().map { it.name }.sorted()
    }

    /**
     * Narrows the table to chosen kinds of operation. The kinds offered are the ones present in the entries at
     * hand, so the popup never lists an operation this reflog does not contain.
     */
    private inner class ActionKindFilter : ComboBoxAction(), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val chosen = filter.actionKinds
            e.presentation.isEnabled = entries.isNotEmpty()
            e.presentation.text = when {
                chosen.isEmpty() -> GitReflogBundle.message("reflog.filter.action.all")
                chosen.size == 1 -> chosen.first()
                else -> GitReflogBundle.message("reflog.filter.action.several", chosen.size)
            }
        }

        override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
            val group = DefaultActionGroup()
            group.add(DumbAwareAction.create(GitReflogBundle.message("reflog.filter.action.all")) {
                setFilter(filter.copy(actionKinds = emptySet()))
            })
            group.addSeparator()
            entries.map { it.actionKind }.distinct().sorted().forEach { kind -> group.add(ActionKindToggle(kind)) }
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
    }
}
