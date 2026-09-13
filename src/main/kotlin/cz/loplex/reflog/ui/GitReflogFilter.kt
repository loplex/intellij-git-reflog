package cz.loplex.reflog.ui

import cz.loplex.reflog.GitReflogEntry

/**
 * What of the loaded reflog the tab shows.
 *
 * Filtering happens over the entries already read rather than through git options, which keeps it instant and
 * keeps the set of action kinds offered to the user honest - they are collected from the entries at hand.
 */
internal data class GitReflogFilter(
    /** Matched against the description, the action, the selector and the beginning of the hash. */
    val text: String = "",
    /** Action kinds to keep. Empty means every kind passes. */
    val actionKinds: Set<String> = emptySet(),
) {
    fun matches(entry: GitReflogEntry): Boolean =
        (actionKinds.isEmpty() || entry.actionKind in actionKinds) && matchesText(entry)

    private fun matchesText(entry: GitReflogEntry): Boolean {
        if (text.isBlank()) return true
        val text = text.trim()
        return entry.description.contains(text, ignoreCase = true) ||
                entry.action.contains(text, ignoreCase = true) ||
                entry.selector.contains(text, ignoreCase = true) ||
                entry.hash.startsWith(text, ignoreCase = true)
    }
}
