package bps.console.menu

import bps.console.app.MenuSession
import kotlin.math.max
import kotlin.math.min

open class ScrollingSelectionMenu<T>(
    override val header: String?,
    override val prompt: String = "Enter selection: ",
    val limit: Int = 30,
    val offset: Int = 0,
    itemListGenerator: (Int, Int) -> List<T>,
    extraItems: List<MenuItem> = emptyList(),
    labelGenerator: T.() -> String = { toString() },
    actOnSelectedItem: (MenuSession, T) -> Unit,
) : Menu {

    override val shortcutMap: MutableMap<String, MenuItem> = mutableMapOf()

    constructor(
        header: String?,
        prompt: String = "Enter selection: ",
        limit: Int = 30,
        offset: Int = 0,
        baseList: List<T>,
        extraItems: List<MenuItem> = emptyList(),
        labelGenerator: T.() -> String = { toString() },
        next: (MenuSession, T) -> Unit,
    ) : this(
        header = header,
        prompt = prompt,
        limit = limit,
        offset = offset,
        itemListGenerator = @Suppress("NAME_SHADOWING")
        { limit, offset -> baseList.subList(offset, min(baseList.size, offset + limit)) },
        extraItems = extraItems,
        labelGenerator = labelGenerator,
        actOnSelectedItem = next,
    )

    init {
        require(limit > 0) { "limit must be > 0" }
    }

    private fun MutableList<MenuItem>.incorporateItem(menuItem: MenuItem) {
        add(menuItem)
        if (menuItem.shortcut !== null)
            shortcutMap[menuItem.shortcut!!] = menuItem
    }

    override var itemsGenerator: () -> List<MenuItem> =
        {
            itemListGenerator(limit, offset)
                .mapTo(mutableListOf()) { item ->
                    item(item.labelGenerator()) { menuSession: MenuSession ->
                        actOnSelectedItem(menuSession, item)
                    }
                }
                .also { menuItems: MutableList<MenuItem> ->
                    if (menuItems.size == limit) {
                        menuItems.incorporateItem(
                            item("Next Items", "n") { menuSession ->
                                menuSession.pop()
                                menuSession.push(
                                    ScrollingSelectionMenu(
                                        header,
                                        prompt,
                                        limit,
                                        offset + limit,
                                        itemListGenerator,
                                        extraItems,
                                        labelGenerator,
                                        actOnSelectedItem,
                                    ),
                                )
                            },
                        )
                    }
                    if (offset > 0) {
                        menuItems.incorporateItem(
                            item("Previous Items", "p") { menuSession ->
                                menuSession.pop()
                                menuSession.push(
                                    ScrollingSelectionMenu(
                                        header,
                                        prompt,
                                        limit,
                                        max(offset - limit, 0),
                                        itemListGenerator,
                                        extraItems,
                                        labelGenerator,
                                        actOnSelectedItem,
                                    ),
                                )
                            },
                        )
                    }
                    extraItems.forEach { menuItems.incorporateItem(it) }
                    menuItems.incorporateItem(backItem)
                    menuItems.incorporateItem(quitItem)
                }
        }
}
