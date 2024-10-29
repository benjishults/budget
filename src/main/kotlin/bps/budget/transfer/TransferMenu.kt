package bps.budget.transfer

import bps.budget.WithIo
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.InRangeInclusiveSimpleEntryValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun WithIo.transferMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = "Select account to TRANSFER money FROM",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts + (budgetData.categoryAccounts - budgetData.generalAccount),
    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
) { menuSession: MenuSession, transferFromAccount: Account ->
    menuSession.push(
        ScrollingSelectionMenu(
            header = "Select account to TRANSFER money TO (from '${transferFromAccount.name}')",
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
            val max = transferFromAccount.balance
            val min = BigDecimal("0.01").setScale(2)
            val amount: BigDecimal =
                SimplePrompt<BigDecimal>(
                    "Enter the amount to TRANSFER from '${transferFromAccount.name}' into '${transferToAccount.name}' [$min, $max]: ",
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                    validator = InRangeInclusiveSimpleEntryValidator(min, max),
                ) {
                    it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
                }
                    .getResult()
                    ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
            if (amount > BigDecimal.ZERO) {
                val description: String =
                    SimplePromptWithDefault(
                        "Enter description of transaction [transfer from '${transferFromAccount.name}' into '${transferToAccount.name}']: ",
                        defaultValue = "transfer from '${transferFromAccount.name}' into '${transferToAccount.name}'",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                    )
                        .getResult()
                        ?: throw TryAgainAtMostRecentMenuException("No description entered.")
                val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
                val transferTransaction = Transaction.Builder(
                    description,
                    timestamp,
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
                budgetData.commit(transferTransaction)
                budgetDao.transactionDao.commit(transferTransaction, budgetData.id)
                outPrinter.important("Transfer recorded")
            } else {
                outPrinter.important("Must transfer a positive amount.")
            }

        },
    )
}
