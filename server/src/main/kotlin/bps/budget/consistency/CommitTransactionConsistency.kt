package bps.budget.consistency

import bps.budget.model.BudgetData
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.DraftStatus
import bps.budget.model.Transaction
import bps.budget.persistence.TransactionDao
import kotlinx.datetime.Instant

fun commitTransactionConsistently(
    transaction: Transaction,
    transactionDao: TransactionDao,
    budgetData: BudgetData,
) {
    budgetData.commit(transaction)
    transactionDao.commit(transaction, budgetData.id)
}

fun commitCreditCardPaymentConsistently(
    transaction: Transaction,
    allSelectedItems: List<TransactionDao.ExtendedTransactionItem<ChargeAccount>>,
    transactionDao: TransactionDao,
    budgetData: BudgetData,
) {
    budgetData.commit(transaction)
    transactionDao.commitCreditCardPayment(
        allSelectedItems,
        transaction,
        budgetData.id,
    )
}

fun clearCheckConsistently(
    draftTransactionItem: TransactionDao.ExtendedTransactionItem<DraftAccount>,
    timestamp: Instant,
    draftAccount: DraftAccount,
    transactionDao: TransactionDao,
    budgetData: BudgetData,
): Transaction =
    Transaction.Builder(
        draftTransactionItem.transactionDescription,
        timestamp,
        type = Transaction.Type.clearing,
    )
        .apply {
            with(draftAccount) {
                addItemBuilderTo(
                    -draftTransactionItem.amount,
                    this@apply.description,
                    DraftStatus.clearing,
                )
            }
            with(draftTransactionItem.account.realCompanion) {
                addItemBuilderTo(-draftTransactionItem.amount, this@apply.description)
            }
        }
        .build()
        .also { clearingTransaction: Transaction ->
            budgetData.commit(clearingTransaction)
            transactionDao.clearCheck(
                draftTransactionItem
                    .item
                    .build(
                        draftTransactionItem.transaction(
                            budgetId = budgetData.id,
                            accountIdToAccountMap = budgetData.accountIdToAccountMap,
                        ),
                    ),
                clearingTransaction,
                budgetData.id,
            )
        }
