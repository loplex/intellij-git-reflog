package cz.loplex.reflog

import com.intellij.openapi.util.NlsSafe
import git4idea.GitBranch

/**
 * A ref whose reflog can be shown.
 */
internal data class GitReflogRef(
    /** Name as git takes it: `HEAD`, `refs/heads/master`, `refs/remotes/origin/main`, `refs/stash`. */
    val name: @NlsSafe String,
) {
    val kind: Kind = kindOf(name)

    /** Name as the user knows it: `master` rather than `refs/heads/master`. */
    val presentableName: @NlsSafe String
        get() = when (kind) {
            Kind.LOCAL_BRANCH -> name.removePrefix(GitBranch.REFS_HEADS_PREFIX)
            Kind.REMOTE_BRANCH -> name.removePrefix(GitBranch.REFS_REMOTES_PREFIX)
            Kind.STASH -> STASH_PRESENTABLE_NAME
            else -> name
        }

    /** Declaration order is the order the kinds are offered in. */
    enum class Kind { HEAD, LOCAL_BRANCH, REMOTE_BRANCH, STASH, OTHER }

    companion object {
        /**
         * The ref a repository with any commit always has a reflog for, and the one the tab starts on.
         *
         * The name is a compile-time constant of its own because [kindOf] runs while this very instance is being
         * constructed and cannot read it back off [HEAD].
         */
        private const val HEAD_REF = "HEAD"
        val HEAD: GitReflogRef = GitReflogRef(HEAD_REF)

        /** Reflog of this ref is what `git stash list` prints. */
        private const val STASH_REF = "refs/stash"
        private const val STASH_PRESENTABLE_NAME = "stash"

        /** Kinds first, names within a kind: `HEAD`, branches, remote branches, the stash, anything else. */
        val ORDER: Comparator<GitReflogRef> = compareBy({ it.kind.ordinal }, { it.presentableName })

        private fun kindOf(name: String): Kind = when {
            name == HEAD_REF -> Kind.HEAD
            name.startsWith(GitBranch.REFS_HEADS_PREFIX) -> Kind.LOCAL_BRANCH
            name.startsWith(GitBranch.REFS_REMOTES_PREFIX) -> Kind.REMOTE_BRANCH
            name == STASH_REF -> Kind.STASH
            else -> Kind.OTHER
        }
    }
}
