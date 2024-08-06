package bps.console.menu

import bps.console.io.OutPrinter

interface Menu {

    val header: String? get() = null
    val prompt: String get() = "Enter selection: "
    val items: List<MenuItem> get() = emptyList()

    fun print(outPrinter: OutPrinter) {
        items
            .foldIndexed(
                header?.let { header: String -> StringBuilder("$header\n") }
                    ?: StringBuilder(),
            ) { index: Int, builder: StringBuilder, item: MenuItem ->
                // TODO consider doing this once in the MenuItem initializer so that it becomes part of the MenuItem
                //      converter.  Downside of that being that then MenuItems can't be shared between Menus.  Do I care?
                builder.append(String.format("% 2d. ${item.label}\n", index + 1))
            }
            .toString()
            .let { menuString: String ->
                outPrinter(menuString)
            }
        outPrinter(prompt)
    }

    companion object {
        operator fun invoke(
            header: String? = null,
            prompt: String = "Enter selection: ",
            items: MutableList<MenuItem>.() -> Unit,
        ): Menu =
            object : Menu {
                override val header: String? =
                    header
                override val prompt: String =
                    prompt
                override val items: List<MenuItem> =
                    mutableListOf<MenuItem>()
                        .apply { items() }
                        .toList()
            }

    }

}

open class SelectionMenu<T>(
    override val header: String? = null,
    override val prompt: String = "Enter selection: ",
    items: List<T>,
    withBack: Boolean = true,
    withQuit: Boolean = true,
    next: (MenuSession, T) -> Unit,
) : Menu {
    override val items: List<MenuItem> =
        items.map { item ->
            item(
                item.toString(),
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
