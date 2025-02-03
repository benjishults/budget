package bps.budget.transaction

import bps.console.io.WithIo
import bps.budget.consistency.deleteTransactionConsistently
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.ui.formatAsLocalDateTime
import bps.console.app.MenuSession
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.item

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
            add(budgetData.generalAccount)
            addAll(budgetData.categoryAccounts - budgetData.generalAccount)
            addAll(budgetData.realAccounts)
            addAll(budgetData.chargeAccounts)
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
                accountIdToAccountMap = budgetData.accountIdToAccountMap,
                timeZone = budgetData.timeZone,
                outPrinter = outPrinter,
                budgetData = budgetData,
                extraItems = listOf(
                    item({ "Delete a transaction" }, "d") {
                        menuSession.push(
                            TransactionListMenu(
                                header = { "Choose a transaction to DELETE" },
                                prompt = { "Select a transaction to DELETE: " },
                                account = selectedAccount,
                                limit = userConfig.numberOfItemsInScrollingList,
                                transactionDao = transactionDao,
                                budgetId = budgetData.id,
                                accountIdToAccountMap = budgetData.accountIdToAccountMap,
                                timeZone = budgetData.timeZone,
                                outPrinter = outPrinter,
                                budgetData = budgetData,
                            ) { _: MenuSession, extendedTransactionItem: TransactionDao.ExtendedTransactionItem<Account> ->
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

fun WithIo.showRecentRelevantTransactions(
    transactionDao: TransactionDao,
    account: Account,
    budgetData: BudgetData,
    label: String = "Recent transactions",
    filter: (TransactionDao.ExtendedTransactionItem<*>) -> Boolean = { true },
) {
    transactionDao
        .fetchTransactionItemsInvolvingAccount(account, limit = 500)
        .filter(filter)
        .take(NUMBER_OF_TRANSACTION_ITEMS_TO_SHOW_BEFORE_PROMPT)
        .sortedDescending()
        .takeIf { it.isNotEmpty() }
        ?.also { outPrinter("$label\n") }
        ?.forEach { item: TransactionDao.ExtendedTransactionItem<*> ->
            outPrinter(
                String.format(
                    "%s | %,10.2f | %s\n",
                    item.transactionTimestamp
                        .formatAsLocalDateTime(budgetData.timeZone),
                    item.amount,
                    item.description ?: item.transactionDescription,
                ),
            )
        }
}
