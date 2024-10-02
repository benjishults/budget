package bps.console.menu

import bps.console.QuitException

fun interface MenuItemAction : (MenuSession) -> Unit
fun interface IntermediateMenuItemAction<out T> : () -> T

object NoopIntermediateAction : IntermediateMenuItemAction<Unit> {
    override fun invoke() {
    }
}

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
    intermediateAction: IntermediateMenuItemAction<Unit> = NoopIntermediateAction,
): MenuItem =
    item(label) { menuSession: MenuSession ->
        menuSession.pop()
        intermediateAction()
    }

/**
 * @param intermediateAction action to take prior to going back to menu session
 * @param label the display of the menu item
 */
fun takeAction(
    label: String,
    intermediateAction: IntermediateMenuItemAction<Unit>,
): MenuItem =
    takeActionAndPush(label, null, intermediateAction)

/**
 * @param intermediateAction action to take prior to going back to menu session
 * @param to if not `null`, and if calling it produced non-`null`, then the result will be pushed onto the menu session
 * @param label the display of the menu item
 */
fun <T> takeActionAndPush(
    label: String,
    to: ((T) -> Menu?)? = null,
    intermediateAction: IntermediateMenuItemAction<T>,
): MenuItem =
    item(label) { menuSession: MenuSession ->
        val value: T = intermediateAction()
        if (to !== null) {
            val pushing: Menu? = to(value)
            if (pushing !== null) {
                menuSession.push(pushing)
            }
        }
    }

/**
 * @param to will be pushed onto the menu session
 * @param label the display of the menu item
 */
fun pushMenu(
    label: String,
    to: () -> Menu,
): MenuItem =
    item(label) { menuSession: MenuSession ->
        menuSession.push(to())
    }

val quitItem: MenuItem =
    BaseMenuItem("Quit") { throw QuitException() }

val backItem: MenuItem =
    BaseMenuItem("Back") { it.pop() }
