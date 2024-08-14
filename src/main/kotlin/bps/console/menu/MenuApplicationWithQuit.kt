package bps.console.menu

import bps.console.QuitException
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

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
            try { // TODO move this out of while
                menuSession.current()
                    .let { currentMenu: Menu ->
                        val items = currentMenu.print(outPrinter)
                        inputReader()
                            .toIntOrNull()
                            ?.let {
                                items.getOrNull(it - 1)
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
