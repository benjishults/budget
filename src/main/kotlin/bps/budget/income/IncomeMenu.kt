package bps.budget.income

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.MenuSession
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun WithIo.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = "Select account receiving the income:",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts + budgetData.chargeAccounts,
    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
) { _: MenuSession, realAccount: RealAccount ->
    val amount: BigDecimal =
        SimplePrompt(
            "Enter the amount of income into '${realAccount.name}': ",
            inputReader = inputReader,
            outPrinter = outPrinter,
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
            ?: BigDecimal.ZERO.setScale(2)
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
        val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
        val income: Transaction = createIncomeTransaction(description, timestamp, amount, budgetData, realAccount)
        budgetData.commit(income)
        budgetDao.commit(income, budgetData.id)
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
        categoryItemBuilders.add(
            Transaction.ItemBuilder(
                amount,
                categoryAccount = budgetData.generalAccount,
            ),
        )
        realItemBuilders.add(Transaction.ItemBuilder(amount, realAccount = realAccount))
    }
    .build()
