package bps.budget.ui

import bps.budget.model.CategoryAccount
import bps.budget.model.defaultGeneralAccountDescription
import bps.budget.model.defaultGeneralAccountName
import bps.budget.persistence.BudgetDao
import bps.console.inputs.RecursivePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter

interface UiFunctions {
    fun createGeneralAccount(budgetDao: BudgetDao<*>): CategoryAccount
    fun createBasicAccounts(): Boolean
}

class ConsoleUiFunctions(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFunctions {

    override fun createGeneralAccount(budgetDao: BudgetDao<*>): CategoryAccount {
        budgetDao.prepForFirstSave()
        return RecursivePrompt(
            listOf(
                SimplePromptWithDefault<String>(
                    "Enter the name for your \"General\" account",
                    defaultGeneralAccountName,
                    inputReader,
                    outPrinter,
                ),
                SimplePromptWithDefault(
                    """Enter the description for your "General" account""".trimMargin(),
                    defaultGeneralAccountDescription,
                    inputReader,
                    outPrinter,
                ),
            ),
        )
        {
            CategoryAccount(it[0] as String, it[1] as String)
        }
            .getResult()
    }

    override fun createBasicAccounts(): Boolean =
        SimplePromptWithDefault(
            """
            |Looks like this is your first time running Budget.
            |Would you like me to set up some standard accounts?  You can always change them later. """.trimMargin(),
            "Y", inputReader, outPrinter,
        )
        { it == "Y" || it == "y" || it.isNullOrBlank() }
            .getResult()

}
