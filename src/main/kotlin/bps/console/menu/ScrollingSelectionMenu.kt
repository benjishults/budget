package bps.console.menu

import bps.console.app.MenuSession
import kotlin.math.max
import kotlin.math.min

/**
 * @param T the type of the selected item
 */
open class ScrollingSelectionMenu<T>(
    override val header: () -> String?,
    override val prompt: () -> String = { "Enter selection: " },
    val limit: Int = 30,
    val offset: Int = 0,
    protected val itemListGenerator: (Int, Int) -> List<T>,
    protected val extraItems: List<MenuItem> = emptyList(),
    protected val labelGenerator: T.() -> String = { toString() },
    protected val actOnSelectedItem: (MenuSession, T) -> Unit,
) : Menu {

    override val shortcutMap: MutableMap<String, MenuItem> = mutableMapOf()

    constructor(
        header: () -> String?,
        prompt: () -> String = { "Enter selection: " },
        limit: Int = 30,
        offset: Int = 0,
        baseList: List<T>,
        extraItems: List<MenuItem> = emptyList(),
        labelGenerator: T.() -> String = { toString() },
        actOnSelectedItem: (MenuSession, T) -> Unit,
    ) : this(
        header = header,
        prompt = prompt,
        limit = limit,
        offset = offset,
        itemListGenerator = { lim, off -> baseList.subList(off, min(baseList.size, off + lim)) },
        extraItems = extraItems,
        labelGenerator = labelGenerator,
        actOnSelectedItem = actOnSelectedItem,
    )

    init {
        require(limit > 0) { "limit must be > 0" }
        require(offset >= 0) { "offset must be >= 0" }
    }

    protected fun MutableList<MenuItem>.incorporateItem(menuItem: MenuItem) {
        add(menuItem)
        if (menuItem.shortcut !== null)
            shortcutMap[menuItem.shortcut!!] = menuItem
    }

    override var itemsGenerator: () -> List<MenuItem> =
        {
            generateBaseMenuItemList()
                .also { menuItems: MutableList<MenuItem> ->
                    if (menuItems.size == limit) {
                        menuItems.incorporateItem(
                            item("Next Items", "n") { menuSession ->
                                menuSession.pop()
                                menuSession.push(nextPageMenuProducer())
                            },
                        )
                    }
                    if (offset > 0) {
                        menuItems.incorporateItem(
                            item("Previous Items", "p") { menuSession ->
                                menuSession.pop()
                                menuSession.push(previousPageMenuProducer())
                            },
                        )
                    }
                    extraItems.forEach { menuItems.incorporateItem(it) }
                    menuItems.incorporateItem(backItem)
                    menuItems.incorporateItem(quitItem)
                }
        }

    protected open fun previousPageMenuProducer(): ScrollingSelectionMenu<T> =
        ScrollingSelectionMenu(
            header,
            prompt,
            limit,
            max(offset - limit, 0),
            itemListGenerator,
            extraItems,
            labelGenerator,
            actOnSelectedItem,
        )

    protected open fun nextPageMenuProducer(): ScrollingSelectionMenu<T> =
        ScrollingSelectionMenu(
            header,
            prompt,
            limit,
            offset + limit,
            itemListGenerator,
            extraItems,
            labelGenerator,
            actOnSelectedItem,
        )

    protected open fun generateBaseMenuItemList() =
        itemListGenerator(limit, offset)
            .mapTo(mutableListOf()) { item ->
                item(item.labelGenerator()) { menuSession: MenuSession ->
                    actOnSelectedItem(menuSession, item)
                }
            }

}
