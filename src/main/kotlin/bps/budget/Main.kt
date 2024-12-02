@file:JvmName("Budget")

package bps.budget

import bps.budget.ui.ConsoleUiFacade
import bps.config.convertToPath

fun main() {
    val configurations =
        BudgetConfigurations(sequenceOf("budget.yml", convertToPath("~/.config/bps-budget/budget.yml")))
    val uiFunctions = ConsoleUiFacade()

    BudgetApplication(
        uiFunctions,
        configurations,
        uiFunctions.inputReader,
        uiFunctions.outPrinter,
    )
        .use() {
            it.run()
        }
}

