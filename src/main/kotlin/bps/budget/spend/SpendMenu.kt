package bps.budget.spend

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.budget.transaction.chooseRealAccountsThenCategories
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.NonBlankSimpleEntryValidator
import bps.console.inputs.PositiveSimpleEntryValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun WithIo.recordSpendingMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu {
    // TODO move this up to the takeActionAndPush?  The intermediateAction would need to return the Transaction.Builder
    //     and the amount, I guess?  If so, I could add that to a new TransactionContext class with a Transaction.Builder
    //     and an amount.
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the total amount spent: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = PositiveSimpleEntryValidator,
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    val description: String =
        SimplePrompt<String>(
            "Enter description of transaction: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NonBlankSimpleEntryValidator,
        )
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No description entered.")
    val timestamp: Instant =
        getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
    return chooseRealAccountsThenCategories(
        totalAmount = amount,
        runningTotal = amount,
        description = description,
        budgetData = budgetData,
        budgetDao = budgetDao,
        userConfig = userConfig,
        transactionBuilder = Transaction.Builder(
            description,
            timestamp,
        ),
    )
}
