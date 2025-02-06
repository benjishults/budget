package bps.budget.transfer

import bps.console.io.WithIo
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.transaction.showRecentRelevantTransactions
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.InRangeInclusiveStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import java.math.BigDecimal

fun WithIo.transferMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = { "Select account to TRANSFER money FROM" },
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts + (budgetData.categoryAccounts - budgetData.generalAccount),
    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
) { menuSession: MenuSession, transferFromAccount: Account ->
    outPrinter.verticalSpace()
    showRecentRelevantTransactions(transactionDao, transferFromAccount, budgetData)
    menuSession.push(
        ScrollingSelectionMenu(
            header = { "Select account to TRANSFER money TO (from '${transferFromAccount.name}')" },
            limit = userConfig.numberOfItemsInScrollingList,
            baseList = when (transferFromAccount) {
                is CategoryAccount -> buildList {
                    add(budgetData.generalAccount)
                    addAll(budgetData.categoryAccounts - budgetData.generalAccount - transferFromAccount)
                }
                else -> budgetData.realAccounts - transferFromAccount
            },
            labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
        ) { _: MenuSession, transferToAccount: Account ->
            outPrinter.verticalSpace()
            showRecentRelevantTransactions(transactionDao, transferToAccount, budgetData)
            val max = transferFromAccount.balance
            val min = BigDecimal("0.01").setScale(2)
            outPrinter.verticalSpace()
            val amount: BigDecimal =
                SimplePrompt<BigDecimal>(
                    "Enter the AMOUNT to TRANSFER from '${transferFromAccount.name}' into '${transferToAccount.name}' [$min, $max]: ",
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                    validator = InRangeInclusiveStringValidator(min, max),
                ) {
                    // NOTE for SimplePromptWithDefault, the first call to transform might fail.  If it
                    //    does, we want to apply the recovery action
                    it.toCurrencyAmountOrNull() ?: throw IllegalArgumentException("$it is not a valid amount")
                }
                    .getResult()
                    ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
            if (amount > BigDecimal.ZERO) {
                outPrinter.verticalSpace()
                val description: String =
                    SimplePromptWithDefault(
                        "Enter DESCRIPTION of transaction [transfer from '${transferFromAccount.name}' into '${transferToAccount.name}']: ",
                        defaultValue = "transfer from '${transferFromAccount.name}' into '${transferToAccount.name}'",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                    )
                        .getResult()
                        ?: throw TryAgainAtMostRecentMenuException("No description entered.")
                outPrinter.verticalSpace()
                val timestamp: Instant =
                    getTimestampFromUser(
                        timeZone = budgetData.timeZone,
                        clock = clock,
                    )
                        ?.toInstant(budgetData.timeZone)
                        ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
                val transferTransaction = Transaction.Builder(
                    description = description,
                    timestamp = timestamp,
                    type = Transaction.Type.transfer,
                )
                    .apply {
                        with(transferFromAccount) {
                            addItemBuilderTo(-amount)
                        }
                        with(transferToAccount) {
                            addItemBuilderTo(amount)
                        }
                    }
                    .build()
                commitTransactionConsistently(transferTransaction, transactionDao, budgetData)
                outPrinter.important("Transfer recorded")
            } else {
                outPrinter.important("Must transfer a positive amount.")
            }

        },
    )
}
