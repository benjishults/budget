package bps.budget.transaction

import bps.budget.AllMenus
import bps.budget.persistence.files.CategoryAccountConfig
import bps.console.inputs.CompositePrompt
import bps.console.inputs.SimplePrompt
import bps.console.menu.Menu
import bps.console.menu.backItem
import bps.console.menu.quitItem
import bps.console.menu.takeAction

val AllMenus.recordTransactionMenu: Menu
    get() =
        Menu("Re") {
            add(
                takeAction("Create Category Fund") {
                    val categoryAccount: CategoryAccountConfig =
                        CompositePrompt(
                            listOf(
                                SimplePrompt("Name of category: ", inputReader, outPrinter),
                                SimplePrompt<String>("Description", inputReader, outPrinter),
                            ),
                        ) { inputs: List<*> ->
                            CategoryAccountConfig(inputs[0] as String, inputs[1] as String)
                        }
                            .getResult()
                },
            )
            add(
                takeAction("Create Real Fund") {

                },
            )
            add(
                takeAction("Create Draft Fund") {

                },
            )
            add(backItem)
            add(quitItem)
        }
