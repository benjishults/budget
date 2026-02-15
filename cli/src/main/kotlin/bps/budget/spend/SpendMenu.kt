package bps.budget.spend

import bps.budget.UserConfiguration
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.model.TransactionType
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import bps.budget.transaction.chooseRealAccountsThenCategories
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.NonBlankStringValidator
import bps.console.inputs.PositiveStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.io.WithIo
import bps.console.menu.Menu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import java.math.BigDecimal
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun WithIo.recordSpendingMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu {
    // TODO move this up to the takeActionAndPush?  The intermediateAction would need to return the Transaction.Builder
    //     and the amount, I guess?  If so, I could add that to a new TransactionContext class with a Transaction.Builder
    //     and an amount.
    outPrinter.verticalSpace()
    val toBeReimbursed: Boolean = SimplePromptWithDefault(
        basicPrompt = "Is this expected to be reimbursed? [y/N]: ",
        defaultValue = false,
        inputReader = inputReader,
        outPrinter = outPrinter,
        transformer = { it in listOf("y", "Y") },
    )
        .getResult()!!
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
    outPrinter.verticalSpace()
    val description: String =
        SimplePrompt<String>(
            "Enter DESCRIPTION of transaction: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NonBlankStringValidator,
        )
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No description entered.")
    outPrinter.verticalSpace()
    val timestamp: Instant =
        getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
            ?.toInstant(budgetData.timeZone)
            ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
    return chooseRealAccountsThenCategories(
        totalAmount = amount,
        runningTotal = amount,
        transactionBuilder = Transaction.Builder(
            description = description,
            timestamp = timestamp,
            transactionType =
                if (toBeReimbursed)
                    TransactionType.reimburse.name
                else
                    TransactionType.expense.name,
        ),
        description = description,
        budgetData = budgetData,
        transactionDao = transactionDao,
        userConfig = userConfig,
        accountDao = accountDao,
    )
}
