package bps.budget.transaction

import bps.budget.UserConfiguration
import bps.budget.consistency.deleteTransactionConsistently
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AccountTransactionEntity
import bps.budget.persistence.TransactionDao
import bps.budget.ui.formatAsLocalDateTime
import bps.console.app.MenuSession
import bps.console.io.WithIo
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.item
import kotlin.uuid.ExperimentalUuidApi

@Suppress("DefaultLocale")
@OptIn(ExperimentalUuidApi::class)
fun WithIo.manageTransactions(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = { "Select account to manage transactions" },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = buildList {
            addAll(budgetData.realAccounts)
            addAll(budgetData.chargeAccounts)
            addAll(budgetData.categoryAccounts - budgetData.generalAccount)
            add(budgetData.generalAccount)
        },
        // TODO https://github.com/benjishults/budget/issues/7
//        extraItems = listOf(item("View Inactive Accounts") {}),
        labelGenerator = {
            String.format("%,10.2f | %-15s | %s", balance, name, description)
        },
    ) { menuSession: MenuSession, selectedAccount: Account ->
        menuSession.push(
            TransactionListMenu(
                account = selectedAccount,
                limit = userConfig.numberOfItemsInScrollingList,
                transactionDao = transactionDao,
                budgetId = budgetData.id,
                accountIdToAccountMap = { budgetData.getAccountByIdOrNull(it) },
                timeZone = budgetData.timeZone,
                outPrinter = outPrinter,
                budgetData = budgetData,
                extraItems = listOf(
                    item({ "Delete a transaction" }, "d") {
                        (it.current() as TransactionListMenu<*>).popContext()

                        menuSession.push(
                            TransactionListMenu(
                                header = { "Choose a transaction to DELETE" },
                                prompt = { "Select a transaction to DELETE: " },
                                account = selectedAccount,
                                limit = userConfig.numberOfItemsInScrollingList,
                                transactionDao = transactionDao,
                                budgetId = budgetData.id,
                                accountIdToAccountMap = { budgetData.getAccountByIdOrNull(it) },
                                timeZone = budgetData.timeZone,
                                outPrinter = outPrinter,
                                budgetData = budgetData,
                            ) { menuSession: MenuSession, extendedTransactionItem: AccountTransactionEntity ->
                                (menuSession.current() as TransactionListMenu<*>).popContext()
                                deleteTransactionConsistently(
                                    extendedTransactionItem,
                                    transactionDao,
                                    accountDao,
                                    budgetData,
                                )
                            },
                        )
                    },
                ),
            ),
        )
    }

const val NUMBER_OF_TRANSACTION_ITEMS_TO_SHOW_BEFORE_PROMPT = 6

@OptIn(ExperimentalUuidApi::class)
@Suppress("DefaultLocale")
fun WithIo.showRecentRelevantTransactions(
    transactionDao: TransactionDao,
    account: Account,
    budgetData: BudgetData,
    label: String = "Recent transactions",
    filter: (AccountTransactionEntity) -> Boolean = { true },
) {
    transactionDao
        .fetchTransactionItemsInvolvingAccount(
            accountId = account.id,
            limit = 500,
            offset = 0,
            types = emptyList(),
            balanceAtStartOfPage = null,
            budgetId = budgetData.id,
        )
        .filter(filter)
        .take(NUMBER_OF_TRANSACTION_ITEMS_TO_SHOW_BEFORE_PROMPT)
        .sortedDescending()
        .takeIf { it.isNotEmpty() }
        ?.also { outPrinter("$label\n") }
        ?.forEach { item: AccountTransactionEntity ->
            outPrinter(
                String.format(
                    "%s | %,10.2f | %s\n",
                    item.timestamp
                        .formatAsLocalDateTime(budgetData.timeZone),
                    item.amount,
                    item.description ?: item.transactionDescription,
                ),
            )
        }
}
