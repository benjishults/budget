package bps.budget.transaction

import bps.budget.WithIo
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.console.app.MenuSession
import bps.console.inputs.userSaysYes
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.item

fun WithIo.manageTransactions(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
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
            ManageTransactionsMenu(
                account = selectedAccount,
                limit = userConfig.numberOfItemsInScrollingList,
                budgetDao = budgetDao,
                budgetId = budgetData.id,
                accountIdToAccountMap = budgetData.accountIdToAccountMap,
                timeZone = budgetData.timeZone,
                outPrinter = outPrinter,
                budgetData = budgetData,
                extraItems = listOf(
                    item("Delete a transaction", "d") {
                        menuSession.push(
                            ManageTransactionsMenu(
                                header = { "Choose a transaction to DELETE" },
                                prompt = { "Select a transaction to DELETE: " },
                                account = selectedAccount,
                                limit = userConfig.numberOfItemsInScrollingList,
                                budgetDao = budgetDao,
                                budgetId = budgetData.id,
                                accountIdToAccountMap = budgetData.accountIdToAccountMap,
                                timeZone = budgetData.timeZone,
                                outPrinter = outPrinter,
                                budgetData = budgetData,
                            ) { _: MenuSession, extendedTransactionItem: TransactionDao.ExtendedTransactionItem<Account> ->
                                with(budgetDao.accountDao) {
                                    with(ViewTransactionFixture) {
                                        outPrinter.showTransactionDetailsAction(
                                            extendedTransactionItem.transaction(
                                                budgetData.id,
                                                budgetData.accountIdToAccountMap,
                                            ),
                                            budgetData.timeZone,
                                        )
                                    }
                                    if (userSaysYes("Are you sure you want to DELETE that transaction?"))
                                        budgetDao.transactionDao.deleteTransaction(
                                            transactionId = extendedTransactionItem.transactionId,
                                            budgetId = budgetData.id,
                                            accountIdToAccountMap = budgetData.accountIdToAccountMap,
                                        )
                                            .updateBalances(budgetId = budgetData.id)
                                }
                            },
                        )
                    },
                ),
            ),
        )
    }
