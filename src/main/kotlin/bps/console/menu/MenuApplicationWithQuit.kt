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

    /**
     * Automatically calls [close] when quit
     */
    override fun run() = try { // TODO move this out of while
        while (true) {
            menuSession.current()
                .run {// currentMenu: Menu ->
                    val items: List<MenuItem> = itemsGenerator().print(outPrinter)
                    getSelection(items)
                        ?.action
                        ?.invoke(menuSession)
                        ?: this
                }
        }
    } catch (quit: QuitException) {
        quitAction(quit)
    }

    private fun Menu.getSelection(items: List<MenuItem>): MenuItem? =
        inputReader()
            .let { inputString ->
                this.shortcutMap[inputString]
                    ?: inputString
                        .toIntOrNull()
                        ?.let {
                            items.getOrNull(it - 1)
                        }
                // TODO add an optional error message
            }

    override fun close() {
        menuSession.close()
    }

}
