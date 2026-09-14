package cz.loplex.reflog.ui

import com.intellij.openapi.actionSystem.DataKey
import cz.loplex.reflog.GitReflogDiffModes
import cz.loplex.reflog.GitReflogEntry
import cz.loplex.reflog.GitReflogSelection
import git4idea.repo.GitRepository

/**
 * Data the reflog tab publishes into the action system, so that its actions do not need a reference to the panel.
 */
internal object GitReflogDataKeys {
    val PANEL: DataKey<GitReflogPanel> = DataKey.create("GitReflog.Panel")
    val REPOSITORY: DataKey<GitRepository> = DataKey.create("GitReflog.Repository")
    val SELECTED_ENTRIES: DataKey<List<GitReflogEntry>> = DataKey.create("GitReflog.SelectedEntries")

    /**
     * Which readings of the selected entries are on offer, and which one the file pane is showing. Published
     * rather than read off the panel because working it out reads the table, which only the EDT may do.
     */
    val DIFF_MODES: DataKey<GitReflogDiffModes> = DataKey.create("GitReflog.DiffModes")

    /**
     * What the table has selected, against the whole reflog it was selected from - which is what a reading needs
     * to be read, the entry before the oldest selected one being one side of several of them.
     *
     * [SELECTED_ENTRIES] is the same selection without the reflog behind it, and is what the actions over a single
     * entry work from.
     */
    val SELECTION: DataKey<GitReflogSelection> = DataKey.create("GitReflog.Selection")
}
