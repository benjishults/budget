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
                builder.append(String.format("%2d. ${item.label}\n", index + 1))
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
