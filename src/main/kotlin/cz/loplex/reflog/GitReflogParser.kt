package cz.loplex.reflog

/**
 * Turns the output of `git reflog show` into entries.
 *
 * Separate from [GitReflogReader] so that the parsing can be exercised without a repository: the output format is
 * the plugin's own, and every one of its quirks is a decision made here.
 */
internal object GitReflogParser {

    /**
     * Separates the fields of one record. Chosen because git never emits it: the placeholders of the format all
     * expand to a single line, and none of them can produce a control character of its own.
     */
    const val FIELD_SEPARATOR: Char = '\u0001'

    /** Number of fields the format produces; the last one is the commit subject. */
    const val FIELD_COUNT: Int = 5

    private const val SELECTOR_SEPARATOR = "@{"

    /**
     * Parses one output line into an entry, or answers null for a line that does not hold a whole record.
     *
     * [index] is the position of the record in the output and becomes the index of the selector: `%gD`/`%gd`
     * render *either* the index *or* the date, never both, and the date is the one git cannot be asked for
     * otherwise. The position holds as the index because a reflog is always read from its newest record.
     *
     * [ref] decides how the reflog message is split, which differs for the stash - see [actionOf].
     */
    fun parse(index: Int, line: String, ref: GitReflogRef): GitReflogEntry? {
        // The subject of the commit comes last and is the only field allowed to hold a separator of its own.
        val fields = line.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
        if (fields.size < FIELD_COUNT) return null
        val (hash, dateSelector, reflogSubject, author, commitSubject) = fields

        return GitReflogEntry(
            selector = dateSelector.substringBefore(SELECTOR_SEPARATOR) + SELECTOR_SEPARATOR + index + "}",
            hash = hash,
            timestamp = parseTimestamp(dateSelector),
            action = actionOf(reflogSubject, ref),
            description = descriptionOf(reflogSubject, ref),
            author = author,
            subject = commitSubject,
        )
    }

    /**
     * Reflog subjects are written as "<action>: <details>", e.g. "checkout: moving from master to feature", and
     * the part before the colon is what the entry did.
     *
     * The stash is the exception: its subjects read "WIP on master: eddeef8 first", where the colon separates the
     * branch from the commit and nothing in the subject is an action. Every stash entry does the same thing, so
     * there is no action to show and none to filter by either.
     *
     * Entries written by older git versions or by scripts may carry no colon at all, and are taken as an action
     * with no details.
     */
    private fun actionOf(subject: String, ref: GitReflogRef): String =
        if (ref.kind == GitReflogRef.Kind.STASH) "" else subject.substringBefore(':').trim()

    private fun descriptionOf(subject: String, ref: GitReflogRef): String =
        if (ref.kind == GitReflogRef.Kind.STASH) subject.trim()
        else subject.substringAfter(':', missingDelimiterValue = "").trim()

    /** Extracts the seconds out of a `HEAD@{1789253693}` selector. */
    private fun parseTimestamp(dateSelector: String): Long {
        val seconds = dateSelector.substringAfter(SELECTOR_SEPARATOR, missingDelimiterValue = "").substringBefore('}')
        return (seconds.toLongOrNull() ?: 0L) * 1000L
    }
}
