package bps.budget.customize

import bps.budget.AllMenus
import bps.budget.persistence.CategoryAccountConfig
//import bps.budget.model.CategoryAccount
import bps.console.inputs.Prompt
import bps.console.inputs.PromptWithDefault
import bps.console.inputs.collectInputs
import bps.console.menu.Menu
import bps.console.menu.backItem
import bps.console.menu.menuBuilder
import bps.console.menu.quitItem
import bps.console.menu.takeAction

val AllMenus.createVirtualFundMenu: Menu
    get() =
        menuBuilder {

        }

val AllMenus.customizeMenu: Menu
    get() =
        menuBuilder("Customize!") {
            add(
                takeAction("Create Virtual Fund") {
                    val virtualAccount: CategoryAccountConfig =
                        collectInputs(
                            listOf(
                                Prompt("Name of category: ", inputReader, outPrinter),
                                PromptWithDefault("Description", "\"\"", inputReader, outPrinter),
                            ),
                        ) { inputs: List<String> ->
                            CategoryAccountConfig(inputs[0], inputs[1])
                        }
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
