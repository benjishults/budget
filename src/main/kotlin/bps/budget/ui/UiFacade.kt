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
import kotlinx.datetime.TimeZone
import java.math.BigDecimal

interface UiFacade {
    fun createGeneralAccount(budgetDao: BudgetDao): CategoryAccount
    fun userWantsBasicAccounts(): Boolean
    fun announceFirstTime(): Unit
    fun getInitialBalance(account: String, description: String): BigDecimal
    fun getDesiredTimeZone(): TimeZone
    fun info(infoMessage: String)
}

class ConsoleUiFacade(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFacade {

    override fun createGeneralAccount(budgetDao: BudgetDao): CategoryAccount {
        budgetDao.prepForFirstSave()
        return RecursivePrompt(
            listOf(
                SimplePromptWithDefault(
                    "Enter the name for your \"General\" account [$defaultGeneralAccountName]: ",
                    defaultGeneralAccountName,
                    inputReader,
                    outPrinter,
                ),
                SimplePromptWithDefault(
                    "Enter the description for your \"General\" account [$defaultGeneralAccountDescription]: ",
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
            "Would you like me to set up some standard accounts?  You can always change and rename them later. [Y] ",
            true,
            inputReader,
            outPrinter,
        )
        { it == "Y" || it == "y" || it.isBlank() }
            .getResult()

    override fun announceFirstTime() {
        outPrinter("Looks like this is your first time running Budget.\n")
    }

    override fun getInitialBalance(account: String, description: String): BigDecimal =
        SimplePromptWithDefault(
            "How much do you currently have in account '$account' [0.00]? ",
            BigDecimal.ZERO.setScale(2),
            inputReader,
            outPrinter,
        ) { BigDecimal(it).setScale(2) }
            .getResult()

    override fun getDesiredTimeZone(): TimeZone =
        SimplePromptWithDefault(
            "Select the time-zone you want dates to appear in [${TimeZone.currentSystemDefault().id}]: ",
            TimeZone.currentSystemDefault(),
            inputReader,
            outPrinter,
        ) { TimeZone.of(it) }
            .getResult()

    override fun info(infoMessage: String) {
        outPrinter("$infoMessage\n")
    }

}
