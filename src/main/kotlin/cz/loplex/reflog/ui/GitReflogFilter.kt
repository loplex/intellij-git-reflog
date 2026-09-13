package cz.loplex.reflog.ui

import cz.loplex.reflog.GitReflogEntry

/**
 * What of the loaded reflog the tab shows.
 *
 * Filtering happens over the entries already read rather than through git options, which keeps it instant and
 * keeps the set of action kinds offered to the user honest - they are collected from the entries at hand.
 */
internal data class GitReflogFilter(
    /**
     * Matched against everything the table shows as text - the reflog message and its action, the selector, the
     * commit's subject and author - and against the beginning of the hash, so a pasted prefix finds its entry.
     */
    val text: String = "",
    /**
     * Action kinds to drop. Empty means every kind passes.
     *
     * Kept as the kinds excluded rather than the kinds kept, because the kinds on offer are collected from the
     * entries read so far: a Load More can bring a kind that was not around when the filter was set, and one
     * named in a list of exclusions is the only one that should then be dropped.
     */
    val excludedActionKinds: Set<String> = emptySet(),
) {
    fun matches(entry: GitReflogEntry): Boolean = entry.actionKind !in excludedActionKinds && matchesText(entry)

    private fun matchesText(entry: GitReflogEntry): Boolean {
        if (text.isBlank()) return true
        val text = text.trim()
        return entry.description.contains(text, ignoreCase = true) ||
                entry.action.contains(text, ignoreCase = true) ||
                entry.selector.contains(text, ignoreCase = true) ||
                entry.subject.contains(text, ignoreCase = true) ||
                entry.author.contains(text, ignoreCase = true) ||
                entry.hash.startsWith(text, ignoreCase = true)
    }
}
