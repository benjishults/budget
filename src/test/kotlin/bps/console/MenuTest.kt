package bps.console

import bps.console.app.MenuApplicationWithQuit
import bps.console.menu.Menu
import bps.console.menu.popMenuItem
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import bps.console.menu.takeActionAndPush
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize

class MenuTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val inputs: MutableList<String> = mutableListOf()
    override val outputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        "basic" {
            val bottomMenu: Menu =
                Menu({ "bottom" }) {
                    add(
                        takeAction("something else") {
                            outPrinter("doing the thing\n")
                        },
                    )
                    add(
                        popMenuItem {
                            outPrinter("backing up\n")
                        },
                    )
                    add(quitItem)
                }
            val topMenu: Menu =
                Menu({ "top" }) {
                    add(
                        takeActionAndPush(
                            label = "something",
                            to = { bottomMenu },
                        ) {
                            outPrinter("taking some action\n")
                        },
                    )
                    add(quitItem)
                }
            inputs.addAll(listOf("1", "2", "2"))
            MenuApplicationWithQuit(topMenu, inputReader, outPrinter)
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                """
                      |top
                      | 1. something
                      | 2. Quit (q)
                      |""".trimMargin(),
                "Enter selection: ",
                "taking some action\n",
                """
                      |bottom
                      | 1. something else
                      | 2. Back
                      | 3. Quit (q)
                      |""".trimMargin(),
                "Enter selection: ",
                "backing up\n",
                """
                      |top
                      | 1. something
                      | 2. Quit (q)
                      |""".trimMargin(),
                "Enter selection: ",
                """
Quitting

""",
            )
            inputs shouldHaveSize 0
        }
        "shortcuts" {
            val bottomMenu =
                Menu({ "bottom" }) {
                    add(
                        takeAction("something else") {
                            outPrinter("doing the thing\n")
                        },
                    )
                    add(
                        popMenuItem(shortcut = "b") {
                            outPrinter("backing up\n")
                        },
                    )
                    add(quitItem)
                }
            val topMenu: Menu =
                Menu({ "top" }) {
                    add(
                        takeActionAndPush(
                            label = "something",
                            to = { bottomMenu },
                        ) {
                            outPrinter("taking some action\n")
                        },
                    )
                    add(quitItem)
                }
            inputs.addAll(listOf("1", "b", "q"))
            MenuApplicationWithQuit(topMenu, inputReader, outPrinter)
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                """
                      |top
                      | 1. something
                      | 2. Quit (q)
                      |""".trimMargin(),
                "Enter selection: ",
                "taking some action\n",
                """
                      |bottom
                      | 1. something else
                      | 2. Back (b)
                      | 3. Quit (q)
                      |""".trimMargin(),
                "Enter selection: ",
                "backing up\n",
                """
                      |top
                      | 1. something
                      | 2. Quit (q)
                      |""".trimMargin(),
                "Enter selection: ",
                """
Quitting

""",
            )
            inputs shouldHaveSize 0
        }
    }

}
