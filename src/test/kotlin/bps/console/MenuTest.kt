package bps.console

import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.menuBuilder
import bps.console.menu.popMenuItem
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import bps.console.menu.takeActionAndPush
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize

//import io.mockk.confirmVerified
//import io.mockk.every
//import io.mockk.mockk
//import io.mockk.verify
//import io.mockk.verifySequence

class MenuTest : FreeSpec() {


    init {
        val outputs: MutableList<String> = mutableListOf()
        val outPrinter: OutPrinter = OutPrinter { outputs.add(it) }
        val inputs: MutableList<String> = mutableListOf()
        val inputReader: InputReader = InputReader { inputs.removeFirst() }
        "basic" {
            val bottomMenu: Menu =
                menuBuilder("bottom") {
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
                menuBuilder("top") {
                    add(
                        takeActionAndPush("something", bottomMenu) {
                            outPrinter("taking some action\n")
                        },
                    )
                    add(quitItem)
                }
            val application: MenuApplication = MenuApplicationWithQuit(topMenu, inputReader, outPrinter)
            inputs.addAll(listOf("1", "2", "2"))
            application.run()
            outputs shouldContainExactly listOf(
                """
                      |top
                      | 1. something
                      | 2. Quit
                      |""".trimMargin(),
                "Enter selection: ",
                "taking some action\n",
                """
                      |bottom
                      | 1. something else
                      | 2. Back
                      | 3. Quit
                      |""".trimMargin(),
                "Enter selection: ",
                "backing up\n",
                """
                      |top
                      | 1. something
                      | 2. Quit
                      |""".trimMargin(),
                "Enter selection: ",
                "Quitting\n",
            )
            inputs shouldHaveSize 0
        }
    }

}
