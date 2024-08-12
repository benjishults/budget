package bps.console.menu

open class SelectionMenu<T>(
    override val header: String?,
    override val prompt: String = "Enter selection: ",
    items: List<T>,
    labelSelector: T.() -> String = { toString() },
    withBack: Boolean = true,
    withQuit: Boolean = true,
    next: (MenuSession, T) -> Unit,
) : Menu {
    override val items: List<MenuItem> =
        items.map { item ->
            item(
                item.labelSelector(),
            ) { session ->
                next(session, item)
            }
        }
            .let { menuItems ->
                if (withBack)
                    menuItems + backItem
                else
                    menuItems
            }
            .let { menuItems ->
                if (withQuit)
                    menuItems + quitItem
                else
                    menuItems
            }
}
