package bps.console.menu

import bps.console.app.CancelHandler
import bps.console.app.TryAgainAtMostRecentMenu
import bps.console.io.OutPrinter

interface Menu {

    val header: () -> String? get() = { null }
    val prompt: () -> String get() = { "Enter selection: " }

    /**
     * Returns the complete list of [MenuItem]s
     */
    val itemsGenerator: () -> List<MenuItem> get() = { emptyList() }
    val shortcutMap: Map<String, MenuItem>

    val cancelHandler: CancelHandler
        get() = TryAgainAtMostRecentMenu

    fun List<MenuItem>.print(outPrinter: OutPrinter): List<MenuItem> =
        apply {
            foldIndexed(
                header()
                    ?.let { header: String ->
                        StringBuilder("$header\n")
                    }
                    ?: StringBuilder(),
            ) { index: Int, builder: StringBuilder, item: MenuItem ->
                // TODO consider doing this once in the MenuItem initializer so that it becomes part of the MenuItem
                //      converter.  Downside of that being that then MenuItems can't be shared between Menus.  Do I care?
                builder.append(String.format("%2d. $item\n", index + 1))
            }
                .toString()
                .let { menuString: String ->
                    outPrinter(menuString)
                }
            outPrinter(prompt())
        }

    class Builder {
        val shortcutMap = mutableMapOf<String, MenuItem>()
        val itemList = mutableListOf<MenuItem>()

        fun add(menuItem: MenuItem) {
            menuItem.shortcut
                ?.let { shortcutMap[it] = menuItem }
            itemList.add(menuItem)
        }
    }

    companion object {
        operator fun invoke(
            header: () -> String? = { null },
            prompt: () -> String = { "Enter selection: " },
            items: Builder.() -> Unit,
        ): Menu {
            val builder = Builder()
                .apply { items() }
            return object : Menu {
                override val header: () -> String? = header
                override val prompt: () -> String = prompt
                override val itemsGenerator: () -> List<MenuItem> = {
                    builder.itemList.toList()
                }
                override val shortcutMap: Map<String, MenuItem> = builder.shortcutMap.toMap()
            }
        }

    }

}
