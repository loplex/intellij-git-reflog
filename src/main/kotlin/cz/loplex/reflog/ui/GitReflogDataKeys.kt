package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.DataKey
import cz.loplex.reflog.GitReflogDiffModes
import cz.loplex.reflog.GitReflogEntry
import git4idea.repo.GitRepository

/**
 * Data the reflog tab publishes into the action system, so that its actions do not need a reference to the panel.
 */
internal object GitReflogDataKeys {
    val PANEL: DataKey<GitReflogPanel> = DataKey.create("GitReflog.Panel")
    val REPOSITORY: DataKey<GitRepository> = DataKey.create("GitReflog.Repository")
    val SELECTED_ENTRIES: DataKey<List<GitReflogEntry>> = DataKey.create("GitReflog.SelectedEntries")

    /**
     * Whether older records remain unread. Published rather than read off the panel so that Load More can update
     * off the EDT like every other action of the tab.
     */
    val HAS_MORE: DataKey<Boolean> = DataKey.create("GitReflog.HasMore")

    /**
     * Which readings of the selected entries are on offer, and which one the file pane is showing. Published
     * rather than read off the panel because working it out reads the table, which only the EDT may do.
     */
    val DIFF_MODES: DataKey<GitReflogDiffModes> = DataKey.create("GitReflog.DiffModes")
}
