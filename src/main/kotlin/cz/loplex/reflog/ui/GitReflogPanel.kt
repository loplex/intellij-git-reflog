package cz.loplex.reflog.ui

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.table.TableView
import cz.loplex.reflog.GitReflogBundle
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogService
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Job
import javax.swing.JComponent

/**
 * Content of the Reflog tab: the reflog of one repository, plus the toolbar to pick it and to reload.
 */
internal class GitReflogPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val tableModel = GitReflogTableModel()
    private val table = TableView(tableModel)
    private var loadJob: Job? = null
    private var disposed = false

    /** Repository whose reflog is currently shown. */
    var repository: GitRepository? = null
        private set

    init {
        table.setShowGrid(false)
        table.tableHeader.reorderingAllowed = false
        tableModel.applyColumnWidths(table)
        TableSpeedSearch.installOn(table)

        setContent(ScrollPaneFactory.createScrollPane(table, true))
        toolbar = createToolbar()

        subscribeToRepositoryChanges()
        selectRepository(GitBranchUtil.getCurrentRepository(project) ?: repositories().firstOrNull())
    }

    /** Switches the tab to [repository] and reads its reflog. */
    fun selectRepository(repository: GitRepository?) {
        this.repository = repository
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

        loadJob = GitReflogService.getInstance(project).loadHeadReflog(
            repository,
            onStarted = { showEmptyText(GitReflogBundle.message("reflog.status.loading")) },
            onFinished = { result -> show(result) },
        )
    }

    private fun show(result: Result<List<GitReflogEntry>>) {
        result
            .onSuccess { entries ->
                tableModel.items = ArrayList(entries)
                table.emptyText.text = GitReflogBundle.message("reflog.status.empty")
            }
            .onFailure { error ->
                showEmptyText(GitReflogBundle.message("reflog.status.error", error.message.orEmpty()))
            }
    }

    private fun showEmptyText(text: String) {
        tableModel.items = ArrayList()
        table.emptyText.text = text
    }

    private fun repositories(): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories

    private fun createToolbar(): JComponent {
        val group = DefaultActionGroup(RepositorySelector(), ActionManager.getInstance().getAction(TOOLBAR_GROUP_ID))
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this
        return toolbar.component
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

    companion object {
        const val TAB_NAME: String = "Reflog"
        private const val TOOLBAR_PLACE = "GitReflogToolbar"
        private const val TOOLBAR_GROUP_ID = "GitReflog.Toolbar"
    }
}
