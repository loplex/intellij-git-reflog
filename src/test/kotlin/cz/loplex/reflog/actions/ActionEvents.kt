package cz.loplex.reflog.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation

/**
 * Asking an action what it makes of a data context, which is what a toolbar and a menu do to decide how to draw
 * it - and what lets the drawing be settled by a test rather than by clicking through a running IDE.
 *
 * [AnAction.update] is a function from a context to a presentation, so handing it a context built by hand reads
 * back the same answer a toolbar would get.
 */
internal fun updated(action: AnAction, context: DataContext): Presentation {
    // A copy of the action's own template, which is what the platform hands it - not a blank one. Actions carry
    // defaults on that template (a ToggleAction, for one, leaves a popup open on being performed), and a blank
    // presentation quietly drops them, leaving a test agreeing with itself about a default nobody uses.
    val event = AnActionEvent.createEvent(
        context,
        action.templatePresentation.clone(),
        ActionPlaces.UNKNOWN,
        ActionUiKind.POPUP,
        null,
    )
    action.update(event)
    return event.presentation
}
