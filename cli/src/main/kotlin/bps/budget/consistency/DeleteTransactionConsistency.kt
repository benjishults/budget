package bps.budget.consistency

import bps.budget.model.BudgetData
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AccountTransactionEntity
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.TransactionEntity
import bps.budget.transaction.ViewTransactionFixture
import bps.console.inputs.userSaysYes
import bps.console.io.WithIo
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun WithIo.deleteTransactionConsistently(
    transactionItem: AccountTransactionEntity,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    budgetData: BudgetData,
    promptInitial: String = "Are you sure you want to DELETE that transaction?",
    successMessage: String = "Transaction deleted",
) {
    val transactionEntity: TransactionEntity =
        transactionDao.getTransactionOrNull(transactionItem.transactionId, transactionItem.budgetId)!!
    with(accountDao) {
        with(ViewTransactionFixture) {
            outPrinter.showTransactionDetailsAction(
                transactionEntity,
                budgetData.timeZone,
            ) {
                budgetData.getAccountByIdOrNull(it)
            }
        }
        if (userSaysYes(promptInitial)) {
            transactionDao.deleteTransaction(
                transactionId = transactionItem.transactionId,
                budgetId = budgetData.id,
                accountDao,
            )
                .let { balancesToUpdate: List<AccountDao.AccountCommitableTransactionItem> ->
                    budgetData.commit(balancesToUpdate)
                }
        }
        outPrinter.important(successMessage)

    }
}
