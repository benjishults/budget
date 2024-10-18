package bps.budget.income

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.PositiveSimpleEntryValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun WithIo.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
    // TODO make this take the amount first and distribute among accounts?
): Menu = ScrollingSelectionMenu(
    header = "Select account receiving the INCOME:",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts + budgetData.chargeAccounts,
    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
) { _: MenuSession, realAccount: RealAccount ->
    val amount: BigDecimal =
        SimplePrompt(
            "Enter the amount of INCOME into '${realAccount.name}': ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = PositiveSimpleEntryValidator,
        ) {
            it.toCurrencyAmountOrNull()!!
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    if (amount <= BigDecimal.ZERO.setScale(2)) {
        outPrinter.important("Not recording non-positive income.")
    } else {
        val description: String =
            SimplePromptWithDefault(
                "Enter description of income [income into '${realAccount.name}']: ",
                defaultValue = "income into '${realAccount.name}'",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No description entered.")
        val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
        val incomeTransaction: Transaction =
            createIncomeTransaction(description, timestamp, amount, budgetData, realAccount)
        budgetData.commit(incomeTransaction)
        budgetDao.commit(incomeTransaction, budgetData.id)
        outPrinter.important("Income recorded")
    }
}

fun createIncomeTransaction(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
) = Transaction.Builder(description, timestamp)
    .apply {
        with(budgetData.generalAccount) {
            addItem(amount)
        }
        with(realAccount) {
            addItem(amount)
        }
    }
    .build()
