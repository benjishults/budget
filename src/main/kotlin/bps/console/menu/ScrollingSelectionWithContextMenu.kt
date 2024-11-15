package bps.console.menu

import bps.console.app.MenuSession

/**
 * @param T the type of the selected item
 * @param C the type of the rollover context for tracking information from the previously-viewed page
 */
abstract class ScrollingSelectionWithContextMenu<T, C>(
    header: () -> String?,
    prompt: () -> String = { "Enter selection: " },
    limit: Int = 30,
    offset: Int = 0,
    /**
     * It is up to implementors to maintain proper invariants for this stack.
     */
    protected val contextStack: MutableList<C> = mutableListOf(),
    itemListGenerator: (Int, Int) -> List<T>,
    extraItems: List<MenuItem> = emptyList(),
    labelGenerator: T.() -> String = { toString() },
    actOnSelectedItem: (MenuSession, T) -> Unit,
) : ScrollingSelectionMenu<T>(
    header = header,
    prompt = prompt,
    limit = limit,
    offset = offset,
    itemListGenerator = itemListGenerator,
    extraItems = extraItems,
    labelGenerator = labelGenerator,
    actOnSelectedItem = actOnSelectedItem,
) {
    /**
     * This is called on the item list immediately after it is generated.  Implementers may want to
     * push onto the [contextStack], here.
     */
    protected abstract fun List<T>.produceCurrentContext(): C

    override fun generateBaseMenuItemList() =
        itemListGenerator(limit, offset)
            .apply { contextStack.add(produceCurrentContext()) }
            .mapTo(mutableListOf()) { item ->
                item(item.labelGenerator()) { menuSession: MenuSession ->
                    actOnSelectedItem(menuSession, item)
                }
            }

    /**
     * Implementations must pop the [contextStack] when going back a page.
     */
    abstract override fun previousPageMenuProducer(): ScrollingSelectionWithContextMenu<T, C>

}
