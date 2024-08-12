package bps.budget.persistence

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction

/**
 * @param C the [BudgetConfigLookup] type determining how the data is found in the first place.
 */
interface BudgetDao/*<out C : BudgetConfigLookup>*/ : AutoCloseable {
//    val config: C

    /**
     * @throws DataConfigurationException if data isn't found.
     */
    fun load(): BudgetData = TODO()
    fun commit(transaction: Transaction) {}
    fun save(data: BudgetData) {}
    fun deleteAccount(account: Account) {}
    fun prepForFirstSave() {}
    fun prepForFirstLoad() {}
    fun fetchTransactions(
        account: Account,
        data: BudgetData,
        limit: Int = 30,
        offset: Long = 0L,
    ): List<Transaction> =
        emptyList()

}
