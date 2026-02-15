@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.consistency

import bps.budget.model.AccountType
import bps.budget.model.BudgetData
import bps.budget.model.DraftStatus
import bps.budget.model.Transaction
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AccountTransactionEntity
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.TransactionDao.ClearingTransactionItem
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun commitTransactionConsistently(
    transaction: Transaction,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    budgetData: BudgetData,
) {
    budgetData.commit(transaction.allItems())
    try {
        transactionDao.createTransaction(
            description =
                transaction.description,
            timestamp =
                transaction.timestamp,
            transactionType =
                transaction.transactionType,
            items = transaction
                .allItems()
                .map { item: Transaction.Item<*> ->
                    item.toTransactionDaoItem()
                },
            saveBalances =
                true,
            budgetId =
                budgetData.id,
            accountDao =
                accountDao,
        )
    } catch (e: Exception) {
        budgetData.revertBalances(transaction.allItems())
        throw e
    }
}

fun commitCreditCardPaymentConsistently(
    transaction: Transaction,
    allSelectedChargeTransactionIds: List<Uuid>,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    budgetData: BudgetData,
) {
    budgetData.commit(transaction.allItems())
    try {
        transactionDao.createCreditCardPaymentTransaction(
            chargeTransactionsBeingCleared = allSelectedChargeTransactionIds,
            description = transaction.description,
            timestamp = transaction.timestamp,
            items = transaction
                .allItems()
                .map { item: Transaction.Item<*> ->
                    item.toTransactionDaoItem()
                },
            budgetId = budgetData.id,
            accountDao = accountDao,
        )
    } catch (e: Exception) {
        budgetData.revertBalances(transaction.allItems())
        throw e
    }
}

fun clearCheckConsistently(
    draftBeingClearedTransactionItem: AccountTransactionEntity,
    draftAccountRealCompanionId: Uuid,
    timestamp: Instant,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    budgetData: BudgetData,
) {
    val description =
        draftBeingClearedTransactionItem.description ?: draftBeingClearedTransactionItem.transactionDescription
    val clearingTransactionItems = buildList {
        add(
            ClearingTransactionItem(
                amount = -draftBeingClearedTransactionItem.amount,
                description = description,
                accountId = draftBeingClearedTransactionItem.accountId,
                accountType = AccountType.draft.name,
                draftStatus = DraftStatus.clearing.name,
            ),
        )
        add(
            ClearingTransactionItem(
                amount = -draftBeingClearedTransactionItem.amount,
                description = description,
                accountId = draftAccountRealCompanionId,
                accountType = AccountType.real.name,
                draftStatus = DraftStatus.clearing.name,
            ),
        )
    }
    budgetData.commit(clearingTransactionItems)
    try {
        transactionDao.createClearCheckTransaction(
            draftBeingClearedTransactionItem.transactionId,
            "clearing check: '$description'",
            timestamp,
            clearingTransactionItems,
            budgetData.id,
            accountDao,
        )
    } catch (e: Exception) {
        budgetData.revertBalances(clearingTransactionItems)
        throw e
    }
}
