package cz.loplex.reflog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import cz.loplex.reflog.GitReflogBundle
import git4idea.reset.GitResetMode
import javax.swing.JComponent

/**
 * Asks which reset mode to use.
 *
 * The platform has a dialog of its own, but it is built around `VcsFullCommitDetails` loaded from the Log - which
 * is exactly what an unreachable commit does not have. The mode names and descriptions still come from git4idea,
 * so the choice reads the same as the one offered in the Log.
 */
internal class GitReflogResetDialog(
    project: Project,
    private val target: String,
    private val shortHash: String,
) : DialogWrapper(project) {

    /** Mode the user picked; read after [showAndGet] returns true. Bound by the dialog panel, hence not private. */
    var mode: GitResetMode = GitResetMode.getDefault()

    init {
        title = GitReflogBundle.message("reflog.reset.dialog.title")
        setOKButtonText(GitReflogBundle.message("reflog.reset.dialog.button"))
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            label(GitReflogBundle.message("reflog.reset.dialog.description", target, shortHash))
        }
        buttonsGroup {
            GitResetMode.values().forEach { resetMode ->
                row {
                    radioButton(resetMode.getName(), resetMode)
                }.rowComment(resetMode.description)
            }
        }.bind(::mode)
    }
}
