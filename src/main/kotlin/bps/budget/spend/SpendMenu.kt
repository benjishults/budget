package bps.budget.spend

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.budget.transaction.chooseRealAccountsThenCategories
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.NonBlankStringValidator
import bps.console.inputs.PositiveStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun WithIo.recordSpendingMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu {
    // TODO move this up to the takeActionAndPush?  The intermediateAction would need to return the Transaction.Builder
    //     and the amount, I guess?  If so, I could add that to a new TransactionContext class with a Transaction.Builder
    //     and an amount.
    val amount: BigDecimal =
        SimplePrompt(
            "Enter the total AMOUNT spent: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = PositiveStringValidator,
        ) {
            // NOTE the validator ensures this is not null
            it.toCurrencyAmountOrNull()!!
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    val description: String =
        SimplePrompt<String>(
            "Enter DESCRIPTION of transaction: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NonBlankStringValidator,
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
        transactionDao = transactionDao,
        userConfig = userConfig,
        transactionBuilder = Transaction.Builder(
            description,
            timestamp,
        ),
    )
}
