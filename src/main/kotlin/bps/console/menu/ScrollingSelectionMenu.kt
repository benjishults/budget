package bps.console.menu

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
        header,
        prompt,
        limit,
        offset,
        @Suppress("NAME_SHADOWING")
        { limit, offset -> baseList.subList(offset, min(baseList.size, offset + limit)) },
        extraItems,
        labelGenerator,
        next,
    )

    init {
        require(limit > 0) { "limit must be > 0" }
    }

    override var itemsGenerator: () -> List<MenuItem> =
        {
            itemListGenerator(limit, offset)
                .mapTo(mutableListOf()) { item ->
                    item(item.labelGenerator()) { session ->
                        actOnSelectedItem(session, item)
                    }
                }
                .also { menuItems ->
                    if (menuItems.size == limit) {
                        menuItems.add(
                            item("Next Items") { menuSession ->
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
                        menuItems.add(
                            item("Previous Items") { menuSession ->
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
                    menuItems.addAll(extraItems)
                    menuItems.add(backItem)
                    menuItems.add(quitItem)
                }
        }
}
