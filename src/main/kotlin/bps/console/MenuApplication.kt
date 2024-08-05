package bps.console

import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu

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
                        currentMenu.print(outPrinter)
                        inputReader()
                            .toIntOrNull()
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
