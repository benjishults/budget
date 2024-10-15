package bps.console.menu

import bps.console.app.MenuSession
import bps.console.app.QuitException

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

    val shortcut: String?

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
    override val shortcut: String? = null,
    override val action: MenuItemAction,
) : MenuItem {

    init {
        require(shortcut == null || shortcut!!.length <= 2)
    }

    override fun toString(): String =
        "$label${if (shortcut !== null) " ($shortcut)" else ""}"

}

fun item(
    label: String,
    shortcut: String? = null,
    action: MenuItemAction,
): MenuItem =
    BaseMenuItem(label, shortcut, action)

/**
 * Takes the [intermediateAction] if provided and pops the menu session.
 * @param intermediateAction action to take prior to going back to menu session
 * @param label the display of the menu item
 */
fun popMenuItem(
    label: String = "Back",
    shortcut: String? = null,
    intermediateAction: IntermediateMenuItemAction<Unit> = NoopIntermediateAction,
): MenuItem =
    item(label, shortcut) { menuSession: MenuSession ->
        menuSession.pop()
        intermediateAction()
    }

/**
 * @param intermediateAction action to take prior to going back to menu session
 * @param label the display of the menu item
 */
fun takeAction(
    label: String,
    shortcut: String? = null,
    intermediateAction: IntermediateMenuItemAction<Unit>,
): MenuItem =
    takeActionAndPush(label, shortcut, intermediateAction = intermediateAction)

/**
 * @param intermediateAction action to take prior to going back to menu session
 * @param to if not `null`, and if calling it produced non-`null`, then the result will be pushed onto the menu session
 * @param label the display of the menu item
 */
fun <T> takeActionAndPush(
    label: String,
    shortcut: String? = null,
    to: (T) -> Menu? = { null },
    intermediateAction: IntermediateMenuItemAction<T>,
): MenuItem =
    item(label, shortcut) { menuSession: MenuSession ->
        val value: T = intermediateAction()
        val pushing: Menu? = to(value)
        if (pushing !== null) {
            menuSession.push(pushing)
        }
    }

/**
 * @param to will be evaluated and its value pushed onto the menu session
 * @param label the display of the menu item
 */
fun pushMenu(
    label: String,
    shortcut: String? = null,
    to: () -> Menu,
): MenuItem =
    item(label, shortcut) { menuSession: MenuSession ->
        menuSession.push(to())
    }

val quitItem: MenuItem =
    BaseMenuItem("Quit", "q") { throw QuitException() }

val backItem: MenuItem = popMenuItem(shortcut = "b")

//fun cancelItem(inProgress: () -> Transaction.Builder? = { null }): MenuItem =
//    item(
//        "Cancel", "c",
//    ) { throw CancelException(workInProgress = inProgress()) }
////    popMenuItem(label = "Cancel", shortcut = "c")
