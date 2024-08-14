package bps.console.menu

open class SelectionMenu<T>(
    override val header: String?,
    override val prompt: String = "Enter selection: ",
    val itemListGenerator: () -> List<T> = { emptyList() },
    val labelSelector: T.() -> String = { toString() },
    val withBack: Boolean = true,
    val withQuit: Boolean = true,
    val next: (MenuSession, T) -> Unit,
) : Menu {
    override var itemsGenerator: () -> List<MenuItem> =
        {
            itemListGenerator()
                .map { item ->
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
        protected set
}

