package bps.console

import bps.console.menu.Menu

class MenuSession(val topLevelMenu: Menu) {

    private val stack: MutableList<Menu> = mutableListOf()

    fun push(menu: Menu) = stack.add(menu)

    fun popOrNull(): Menu? = stack.removeFirstOrNull()

    fun current(): Menu = stack.firstOrNull() ?: topLevelMenu

//    class Builder {
//        var topLevelMenu: Menu? = null
//        fun build(): MenuSession = MenuSession(topLevelMenu!!)
//    }

}
