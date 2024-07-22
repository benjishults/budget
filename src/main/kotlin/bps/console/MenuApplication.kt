package bps.console

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.MenuItem

open class MenuApplicationWithQuit(
    topLevelMenu: Menu,
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : MenuApplication, AutoCloseable {

    private val menuSession: MenuSession = MenuSession(topLevelMenu)

    open fun quitAction(quitException: QuitException) {
        outPrinter("${quitException.message!!}\n")
    }

    override fun run() {
        while (true) {
            try {
                menuSession.current()
                    .let { currentMenu: Menu ->
                        currentMenu.items
                            .foldIndexed(
                                currentMenu.header?.let { header: String -> StringBuilder("$header\n") }
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
                        outPrinter(currentMenu.prompt ?: "Enter selection: ")
                        inputReader()
                            ?.toIntOrNull()
                            ?.let {
                                currentMenu.items.getOrNull(it - 1)
                                    ?.action
                                    ?.invoke(menuSession)
                                // TODO add an optional error message
                                    ?: this
                            }
                        // TODO add an optional error message
                            ?: this
                    }
            } catch (quit: QuitException) {
                quitAction(quit)
                break
            }
        }
    }

    override fun close() {
    }

}

interface MenuApplication {
    fun run()
}
