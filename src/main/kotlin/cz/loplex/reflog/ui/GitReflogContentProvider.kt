package cz.loplex.reflog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.ui.content.Content
import cz.loplex.reflog.GitReflogBundle
import git4idea.GitVcs
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Builds the Reflog tab of the Git tool window. Called lazily, the first time the tab is selected.
 */
internal class GitReflogContentProvider(private val project: Project) : ChangesViewContentProvider {

    override fun initTabContent(content: Content) {
        val panel = GitReflogPanel(project)
        content.component = panel
        content.setDisposer(panel)
    }
}

/**
 * Shows the tab for projects that have Git among their active version control systems.
 *
 * Deliberately checks the VCS mapping rather than the list of loaded repositories: the mapping is what the platform
 * re-evaluates the tab against, and it is already in place when the Git tool window is built.
 */
internal class GitReflogContentVisibilityPredicate : Predicate<Project> {
    override fun test(project: Project): Boolean =
        // PROBE: the service is asked for by its interface rather than through getInstance(). Both name the same
        // object, but getInstance() sits on a companion in 2025.3 and on neither in 2025.2, so compiling the call
        // emits a getstatic against a field older IDEs have not got. Looking the service up says nothing about
        // where the accessor lives.
        project.getService(ProjectLevelVcsManager::class.java).checkVcsIsActive(GitVcs.NAME)
}

/** Places the tab right behind the Log tab, which is the one it complements. */
internal class GitReflogContentPreloader : ChangesViewContentProvider.Preloader {
    override fun preloadTabContent(content: Content) {
        content.putUserData(
            ChangesViewContentManager.ORDER_WEIGHT_KEY,
            ChangesViewContentManager.TabOrderWeight.VCS_LOG.weight + 1,
        )
    }
}

internal class GitReflogDisplayNameSupplier : Supplier<String> {
    override fun get(): String = GitReflogBundle.message("reflog.tab.name")
}
