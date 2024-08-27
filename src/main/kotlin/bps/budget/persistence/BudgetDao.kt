package bps.budget.persistence

import bps.budget.auth.User
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import java.util.UUID

/**
 * @param C the [BudgetConfig] type determining how the data is found in the first place.
 */
interface BudgetDao/*<out C : BudgetConfigLookup>*/ : AutoCloseable {
//    val config: C

    fun getUserByLogin(login: String): User? = null
    fun createUser(login: String, password: String): UUID =
        UUID.randomUUID()

    fun prepForFirstLoad() {}

    /**
     * @throws DataConfigurationException if data isn't found.
     */
    fun load(budgetId: UUID, userId: UUID): BudgetData = TODO()
    fun prepForFirstSave() {}
    fun save(data: BudgetData, user: User) {}
    fun commit(transaction: Transaction, budgetId: UUID) {}
    fun clearCheck(
        draftTransactionItems: List<Transaction.Item>,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) = Unit

    fun clearCheck(
        draftTransactionItem: Transaction.Item,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) =
        clearCheck(listOf(draftTransactionItem), clearingTransaction, budgetId)

    fun deleteAccount(account: Account) {}
    fun deleteBudget(budgetId: UUID) {}
    fun deleteUser(userId: UUID) {}
    fun deleteUserByLogin(login: String) {}
    fun fetchTransactions(
        account: Account,
        data: BudgetData,
        limit: Int = 30,
        offset: Int = 0,
    ): List<Transaction> =
        emptyList()

    override fun close() {}

}
