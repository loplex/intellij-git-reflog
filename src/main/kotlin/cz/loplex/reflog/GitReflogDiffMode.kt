package cz.loplex.reflog

/**
 * How the selected reflog entries stand to one another in the commit graph.
 *
 * Answering this costs a git call, so the file pane starts on [UNKNOWN] and narrows it once the call comes back.
 */
internal enum class GitReflogAncestry {
    /** Not asked yet. The selection is taken at its word until the answer arrives. */
    UNKNOWN,

    /** Every selected commit is an ancestor of the ones selected after it. */
    LINEAR,

    /** Some selected commit is on a branch the others never reached. */
    DIVERGED,
}

/**
 * What the table has selected, against the reflog it was selected from.
 *
 * The whole reflog comes along because the entry *before* the oldest selected one is what several of the modes
 * diff against, and that entry can well be one the filter hides - the reflog is a timeline whether or not the
 * table is showing all of it.
 */
internal data class GitReflogSelection(
    val ref: GitReflogRef,
    /** Every entry the last read returned, newest first. */
    val entries: List<GitReflogEntry>,
    /** Entries the table has selected. */
    val selected: List<GitReflogEntry>,
) {
    /** Selected entry the ref moved to last. */
    val newest: GitReflogEntry? get() = selected.minByOrNull { positionOf(it) }

    /** Selected entry the ref moved to first. */
    val oldest: GitReflogEntry? get() = selected.maxByOrNull { positionOf(it) }

    /** Where the ref stood before the [oldest] selected entry moved it, if the reflog still reaches that far. */
    val beforeOldest: GitReflogEntry?
        get() = oldest?.let { entries.getOrNull(positionOf(it) + 1) }

    /** Selected entries from the oldest to the newest, which is the order they happened in. */
    val chronological: List<GitReflogEntry> get() = selected.sortedByDescending { positionOf(it) }

    /**
     * A stash reflog is a stack of unrelated entries rather than a timeline of one state, so the modes that read
     * it as a timeline have nothing to say about it.
     */
    val isTimeline: Boolean get() = ref.kind != GitReflogRef.Kind.STASH

    private fun positionOf(entry: GitReflogEntry): Int = entries.indexOf(entry)
}

/**
 * Which question the file pane answers about the selected entries.
 *
 * The reflog can answer more than one, which is what sets it apart from the Log: its entries are ordered in time
 * for a single ref, so "the state before" and "the state after" exist even where the commits are on branches
 * that never met.
 */
internal enum class GitReflogDiffMode {
    /**
     * What the selected movements did to the working tree: the state before the oldest selected entry against
     * the state after the newest one.
     *
     * For a `commit` entry this is the commit's own diff; for a `checkout` or a `reset` it is the far more
     * telling "what changed under me", which no diff against a parent can show.
     */
    REFLOG_STEP,

    /**
     * The difference between the two selected states themselves, leaving out the movement that produced the
     * oldest one: "how does where I am now differ from where I was then".
     */
    BETWEEN_SELECTED,

    /**
     * What each selected commit changed against its own parent, merged into one tree - the way the Log answers a
     * multiple selection.
     */
    UNION,

    /** The selected state against the working tree as it stands now. */
    WORKING_TREE,

    ;

    /**
     * Whether this mode has an answer for [selection].
     *
     * [ancestry] only ever matters to [UNION]: merging the diffs of commits that sit on branches which never met
     * produces a tree of changes that undo one another, which is worse than no answer at all.
     */
    fun isApplicableTo(selection: GitReflogSelection, ancestry: GitReflogAncestry): Boolean {
        val count = selection.selected.size
        if (count == 0) return false

        return when (this) {
            REFLOG_STEP -> selection.isTimeline && selection.beforeOldest != null
            BETWEEN_SELECTED -> selection.isTimeline && count >= 2
            // A single commit against its parent is the one reading that never needs the graph walked.
            UNION -> count == 1 || ancestry != GitReflogAncestry.DIVERGED
            WORKING_TREE -> count == 1
        }
    }

    /**
     * State the mode reads the changes *from*, where that is a state the reflog recorded.
     *
     * Null for [UNION], which has no single one: it reads every selected commit against that commit's own parent.
     */
    fun oldSideOf(selection: GitReflogSelection): GitReflogEntry? = when (this) {
        REFLOG_STEP -> selection.beforeOldest
        BETWEEN_SELECTED -> selection.oldest
        WORKING_TREE -> selection.newest
        UNION -> null
    }

    /**
     * State the mode reads the changes *to*, where that is a state the reflog recorded.
     *
     * Null for [WORKING_TREE], whose other side is the working tree rather than anything the reflog holds, and
     * for [UNION], which has no single one.
     */
    fun newSideOf(selection: GitReflogSelection): GitReflogEntry? = when (this) {
        REFLOG_STEP, BETWEEN_SELECTED -> selection.newest
        WORKING_TREE, UNION -> null
    }

    /**
     * The mode to actually show for [selection]: this one where it fits, otherwise the nearest one that does.
     *
     * Null where nothing fits, which a stash reflog with more than one entry selected is the honest case of.
     *
     * The chosen mode is not rewritten by this - what the user picked stays picked, and comes back as soon as a
     * selection it suits is made again.
     */
    fun effectiveFor(selection: GitReflogSelection, ancestry: GitReflogAncestry): GitReflogDiffMode? =
        (listOf(this) + fallbacks).firstOrNull { it.isApplicableTo(selection, ancestry) }

    /** Modes tried, in order, when this one does not fit the selection. */
    private val fallbacks: List<GitReflogDiffMode>
        get() = when (this) {
            REFLOG_STEP -> listOf(UNION, BETWEEN_SELECTED)
            BETWEEN_SELECTED -> listOf(REFLOG_STEP, UNION)
            UNION -> listOf(BETWEEN_SELECTED, REFLOG_STEP)
            // Asking about the working tree is a question of its own, so the fallbacks lead with the mode that
            // at least keeps the selection's own two ends in the answer.
            WORKING_TREE -> listOf(BETWEEN_SELECTED, REFLOG_STEP, UNION)
        }

    companion object {
        /** The mode the tab opens on: the reading that suits the reflog's own entries best. */
        val DEFAULT: GitReflogDiffMode = REFLOG_STEP
    }
}

/**
 * What the tab publishes about its modes into the action system, so that the switch and the context menu do not
 * need a reference to the panel.
 *
 * A snapshot rather than a live view: working out what fits reads the table, which only the EDT may do, while the
 * actions that show it update off the EDT like every other action of the tab.
 */
internal data class GitReflogDiffModes(
    /** Mode the user picked, which stands whether or not it fits what is selected. */
    val preferred: GitReflogDiffMode,
    /** Mode the file pane is actually showing, null where nothing fits the selection. */
    val effective: GitReflogDiffMode?,
    /** Modes that have an answer for what is selected. */
    val applicable: List<GitReflogDiffMode>,
) {
    companion object {
        /**
         * What the modes stand at for [selection], as the switch and the context menu are to show them.
         *
         * The one place the snapshot is worked out, so that what a test reads is what the tab publishes rather
         * than a second copy of the same rule.
         */
        fun of(
            preferred: GitReflogDiffMode,
            selection: GitReflogSelection,
            ancestry: GitReflogAncestry,
        ): GitReflogDiffModes = GitReflogDiffModes(
            preferred = preferred,
            effective = preferred.effectiveFor(selection, ancestry),
            applicable = GitReflogDiffMode.entries.filter { it.isApplicableTo(selection, ancestry) },
        )
    }
}
