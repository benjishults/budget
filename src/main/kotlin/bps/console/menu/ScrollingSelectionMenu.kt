package bps.console.menu

import kotlin.math.min

open class ScrollingSelectionMenu<T>(
    override val header: String?,
    override val prompt: String = "Enter selection: ",
    val limit: Int = 30,
    val offset: Int = 0,
    itemListGenerator: (Int, Int) -> List<T>,
    labelSelector: T.() -> String = { toString() },
    next: (MenuSession, T) -> Unit,
) : Menu {
    override var itemsGenerator: () -> List<MenuItem> =
        {
            itemListGenerator(limit, offset)
                .mapTo(mutableListOf()) { item ->
                    item(
                        item.labelSelector(),
                    ) { session ->
                        next(session, item)
                    }
                }
                .also { menuItems ->
                    menuItems.add(
                        item("Next Items") { menuSession ->
                            menuSession.popOrNull()
                            menuSession.push(
                                ScrollingSelectionMenu(
                                    header,
                                    prompt,
                                    limit,
                                    offset + limit,
                                    itemListGenerator,
                                    labelSelector,
                                    next,
                                ),
                            )
                        },
                    )
                    if (offset > 0) {
                        menuItems.add(
                            item("Previous Items") { menuSession ->
                                menuSession.popOrNull()
                                menuSession.push(
                                    ScrollingSelectionMenu(
                                        header,
                                        prompt,
                                        limit,
                                        min(offset - limit, 0),
                                        itemListGenerator,
                                        labelSelector,
                                        next,
                                    ),
                                )
                            },
                        )
                    }

                    menuItems.add(backItem)
                    menuItems.add(quitItem)
                }
        }
}
