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
    val event = AnActionEvent.createEvent(context, Presentation(), ActionPlaces.UNKNOWN, ActionUiKind.POPUP, null)
    action.update(event)
    return event.presentation
}
