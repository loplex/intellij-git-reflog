package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.DataKey
import cz.loplex.reflog.GitReflogEntry
import git4idea.repo.GitRepository

/**
 * Data the reflog tab publishes into the action system, so that its actions do not need a reference to the panel.
 */
internal object GitReflogDataKeys {
    val PANEL: DataKey<GitReflogPanel> = DataKey.create("GitReflog.Panel")
    val REPOSITORY: DataKey<GitRepository> = DataKey.create("GitReflog.Repository")
    val SELECTED_ENTRIES: DataKey<List<GitReflogEntry>> = DataKey.create("GitReflog.SelectedEntries")
}
