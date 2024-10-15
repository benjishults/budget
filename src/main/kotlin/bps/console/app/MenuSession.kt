package bps.console.app

import bps.console.menu.Menu

/**
 * Maintains the stack of menus active in an application.
 */
class MenuSession(val topLevelMenu: Menu) : AutoCloseable {

    @Volatile
    private var closed: Boolean = false

    private val stack: MutableList<Menu> =
        mutableListOf()

    fun push(menu: Menu) =
        stack.add(menu)

    /**
     * Pops the top of the stack of menus unless we are on the [topLevelMenu].  Returns the item that was on top.
     */
    fun pop(): Menu =
        stack.removeLastOrNull() ?: topLevelMenu

    fun current(): Menu {
        check(!closed) { "attempt to use menu after closing" }
        return stack
            .lastOrNull()
            ?: topLevelMenu
    }

    override fun close() {
        closed = true
    }

}
