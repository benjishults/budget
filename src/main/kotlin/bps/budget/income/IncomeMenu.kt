package bps.budget.income

import bps.console.io.WithIo
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.model.BudgetData
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.Transaction.Type
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.transaction.showRecentRelevantTransactions
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.PositiveStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import java.math.BigDecimal

fun WithIo.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    userConfig: UserConfiguration,
    clock: Clock,
    // TODO make this take the amount first and distribute among accounts?
): Menu = ScrollingSelectionMenu(
    header = { "Select account receiving the INCOME:" },
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts + budgetData.chargeAccounts,
    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
) { _: MenuSession, realAccount: RealAccount ->

    showRecentRelevantTransactions(
        transactionDao = transactionDao,
        account = realAccount,
        budgetData = budgetData,
        label = "Recent income:",
    ) { transactionItem ->
        budgetData.generalAccount in
                transactionItem.transaction(
                    budgetData.id,
                    budgetData.accountIdToAccountMap,
                )
                    .categoryItems
                    .map { it.account }
    }
    val amount: BigDecimal =
        SimplePrompt(
            "Enter the AMOUNT of INCOME into '${realAccount.name}': ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = PositiveStringValidator,
        ) {
            // NOTE the validator ensures this is not null
            it.toCurrencyAmountOrNull()!!
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    if (amount <= BigDecimal.ZERO.setScale(2)) {
        outPrinter.important("Not recording non-positive income.")
    } else {
        val description: String =
            SimplePromptWithDefault(
                "Enter DESCRIPTION of income [income into '${realAccount.name}']: ",
                defaultValue = "income into '${realAccount.name}'",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No description entered.")
        val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
            ?.toInstant(budgetData.timeZone)
            ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
        commitTransactionConsistently(
            createIncomeTransaction(description, timestamp, amount, budgetData, realAccount),
            transactionDao,
            budgetData,
        )
        outPrinter.important("Income recorded")
    }
}

fun createIncomeTransaction(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
): Transaction = createTransactionAddingToRealAccountAndGeneral(
    description = description,
    timestamp = timestamp,
    type = Type.income,
    amount = amount,
    budgetData = budgetData,
    realAccount = realAccount,
)

fun createInitialBalanceTransaction(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
): Transaction = createTransactionAddingToRealAccountAndGeneral(
    description = description,
    timestamp = timestamp,
    type = Type.initial,
    amount = amount,
    budgetData = budgetData,
    realAccount = realAccount,
)

fun createTransactionAddingToRealAccountAndGeneral(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
    type: Type,
) = Transaction.Builder(
    description = description,
    timestamp = timestamp,
    type = type,
)
    .apply {
        with(budgetData.generalAccount) {
            addItemBuilderTo(amount)
        }
        with(realAccount) {
            addItemBuilderTo(amount)
        }
    }
    .build()
