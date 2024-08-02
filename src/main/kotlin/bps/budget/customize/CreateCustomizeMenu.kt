package bps.budget.customize

import bps.budget.AllMenus
import bps.budget.persistence.files.CategoryAccountConfig
import bps.console.inputs.RecursivePrompt
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.menu.Menu
import bps.console.menu.backItem
import bps.console.menu.menuBuilder
import bps.console.menu.quitItem
import bps.console.menu.takeAction

val AllMenus.customizeMenu: Menu
    get() =
        menuBuilder("Customize!") {
            add(
                takeAction("Create Category Fund") {
                    val categoryAccount: CategoryAccountConfig =
                        RecursivePrompt(
                            listOf(
                                SimplePrompt<String>("Name of category: ", inputReader, outPrinter),
                                SimplePromptWithDefault("Description", "\"\"", inputReader, outPrinter),
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
