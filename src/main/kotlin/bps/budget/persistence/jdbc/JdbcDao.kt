package bps.budget.persistence.jdbc

import bps.budget.data.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.JdbcConfig

class JdbcDao(
    override val config: JdbcConfig,
) : BudgetDao<JdbcConfig> {
    override fun load(): BudgetData {
        TODO("Not yet implemented")
    }

    override fun save(data: BudgetData) {
        TODO("Not yet implemented")
    }
}
