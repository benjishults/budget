package bps.console.menu

class MenuSession(val topLevelMenu: Menu) {

    private val stack: MutableList<Menu> =
        mutableListOf()

    fun push(menu: Menu) =
        stack.add(menu)

    fun popOrNull(): Menu? =
        stack.removeLastOrNull()

    fun current(): Menu =
        stack
            .lastOrNull()
            ?: topLevelMenu

//    class Builder {
//        var topLevelMenu: Menu? = null
//        fun build(): MenuSession = MenuSession(topLevelMenu!!)
//    }

}
