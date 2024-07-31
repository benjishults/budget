package bps.budget.persistence

import bps.budget.data.BudgetData
import bps.budget.model.Account
import bps.budget.transaction.Transaction

/**
 * @param C the [BudgetConfigLookup] type determining how the data is found in the first place.
 */
interface BudgetDao<out C : BudgetConfigLookup> : AutoCloseable {
    val config: C

    /**
     * @throws DataConfigurationException if data isn't found.
     */
    fun load(): BudgetData
    fun commit(transaction: Transaction) {}
    fun save(data: BudgetData) {}
    fun deleteAccount(account: Account) {}
    fun prepForFirstSave() {}
    fun prepForFirstLoad() {}
}
