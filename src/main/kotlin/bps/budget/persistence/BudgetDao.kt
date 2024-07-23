package bps.budget.persistence

import bps.budget.data.BudgetData

interface BudgetDao<C : BudgetConfigLookup> {
    val config: C

    /**
     * @throws DataConfigurationException if data isn't found.
     */
    fun load(): BudgetData
    fun save(data: BudgetData)
}
