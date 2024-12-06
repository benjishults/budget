package bps.budget.consistency

import bps.console.io.WithIo
import bps.budget.model.BudgetData
import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import bps.budget.transaction.ViewTransactionFixture
import bps.console.inputs.userSaysYes

fun WithIo.deleteTransactionConsistently(
    transactionItem: TransactionDao.ExtendedTransactionItem<*>,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    budgetData: BudgetData,
    promptInitial: String = "Are you sure you want to DELETE that transaction?",
    successMessage: String = "Transaction deleted",
) {
    with(accountDao) {
        with(ViewTransactionFixture) {
            outPrinter.showTransactionDetailsAction(
                transactionItem.transaction(
                    budgetData.id,
                    budgetData.accountIdToAccountMap,
                ),
                budgetData.timeZone,
            )
        }
        if (userSaysYes(promptInitial)) {
            transactionDao.deleteTransaction(
                transactionId = transactionItem.transactionId,
                budgetId = budgetData.id,
                accountIdToAccountMap = budgetData.accountIdToAccountMap,
            )
                .updateBalances(budgetId = budgetData.id)
            budgetData.undoTransactionForItem(transactionItem)
        }
        outPrinter.important(successMessage)

    }
}
