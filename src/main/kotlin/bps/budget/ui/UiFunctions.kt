package bps.budget.ui

import bps.budget.data.BudgetData
import bps.budget.persistence.CategoryAccountConfig
import bps.budget.persistence.PersistenceConfiguration
import bps.console.inputs.PromptWithDefault
import bps.console.inputs.collectInputs
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface UiFunctions {
    fun createGeneralAccount(): CategoryAccountConfig
    fun saveData(persistenceConfiguration: PersistenceConfiguration, budgetData: BudgetData)
}

class ConsoleUiFunctions(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFunctions {

    override fun createGeneralAccount(): CategoryAccountConfig =
        collectInputs(
            listOf(
                PromptWithDefault(
                    """
            |Looks like this is your first time running Budget.
            |Enter the name for your "General" account""".trimMargin(),
                    "General",
                    inputReader,
                    outPrinter,
                ),
                PromptWithDefault(
                    """Enter the description for your "General" account""".trimMargin(),
                    "Income is automatically deposited here and allowances are made from here.",
                    inputReader,
                    outPrinter,
                ),
            ),
        ) { CategoryAccountConfig(it[0], it[1]) }

    override fun saveData(persistenceConfiguration: PersistenceConfiguration, budgetData: BudgetData) {
        // TODO for now, let's get the accounts file saved
        val dataDirectory: String = persistenceConfiguration.file.dataDirectory
        val accountsFileName = "accounts.yml"

        budgetData.generalAccount.id
        outPrinter("pretending to save")
    }
}
