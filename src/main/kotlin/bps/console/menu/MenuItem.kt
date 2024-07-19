package bps.console.menu

import bps.console.MenuSession
import bps.console.QuitException

fun interface MenuItemAction : (MenuSession) -> Unit
fun interface IntermediateMenuItemAction : () -> Unit

object NoopIntermediateAction : IntermediateMenuItemAction {
    override fun invoke() {
    }
}

@MenuDslElement
interface MenuItem {
    val label: String

    /**
     * Takes action and updates the [MenuSession].
     */
    val action: MenuItemAction

//    companion object {
//        operator fun invoke(label: String, action: MenuItemAction): MenuItem =
//            object : MenuItem {
//                override val label: String = label
//                override val action: MenuItemAction = action
//            }
//    }

}

open class BaseMenuItem(
    override val label: String,
    override val action: MenuItemAction,
) : MenuItem {
    override fun toString(): String =
        label
}

fun item(
    label: String,
    action: MenuItemAction,
): MenuItem =
    BaseMenuItem(label, action)

/**
 * Takes the [intermediateAction] if provided and pops the menu session.
 * @param intermediateAction action to take prior to going back to menu session
 * @param label the display of the menu item
 */
fun popMenuItem(
    label: String = "Back",
    intermediateAction: IntermediateMenuItemAction = NoopIntermediateAction,
): MenuItem =
    item(label) { menuSession: MenuSession ->
        menuSession.popOrNull()
        intermediateAction()
    }

/**
 * @param intermediateAction action to take prior to going back to menu session
 * @param label the display of the menu item
 */
fun takeAction(
    label: String,
    intermediateAction: IntermediateMenuItemAction,
): MenuItem =
    takeActionAndPush(label, null, intermediateAction)

/**
 * @param intermediateAction action to take prior to going back to menu session
 * @param to if not `null`, this will be pushed onto the menu session
 * @param label the display of the menu item
 */
fun takeActionAndPush(
    label: String,
    to: Menu? = null,
    intermediateAction: IntermediateMenuItemAction,
): MenuItem =
    item(label) { menuSession: MenuSession ->
        intermediateAction()
        to?.let {
            menuSession.push(it)
        }
    }

val quitItem: MenuItem =
    BaseMenuItem("Quit") { throw QuitException() }

val backItem: MenuItem =
    BaseMenuItem("Back") { it.popOrNull() }
