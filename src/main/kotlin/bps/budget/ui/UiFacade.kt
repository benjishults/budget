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
import java.math.BigDecimal

interface UiFacade {
    fun createGeneralAccount(budgetDao: BudgetDao<*>): CategoryAccount
    fun userWantsBasicAccounts(): Boolean
    fun getInitialBalance(account: String, description: String): BigDecimal
    fun info(infoMessage: String)
}

class ConsoleUiFacade(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFacade {

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

    override fun userWantsBasicAccounts(): Boolean =
        SimplePromptWithDefault(
            """
            |Looks like this is your first time running Budget.
            |Would you like me to set up some standard accounts?  You can always change them later. """.trimMargin(),
            "Y", inputReader, outPrinter,
        )
        { it == "Y" || it == "y" || it.isNullOrBlank() }
            .getResult()

    override fun getInitialBalance(account: String, description: String): BigDecimal =
        SimplePromptWithDefault(
            "How much do you currently have in account '$account'? ($description) ",
            "0.00",
            inputReader,
            outPrinter,
        ) { BigDecimal(it).setScale(2) }
            .getResult()

    override fun info(infoMessage: String) {
        outPrinter("$infoMessage\n")
    }

}
