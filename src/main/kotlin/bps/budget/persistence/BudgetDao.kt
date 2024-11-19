package bps.budget.persistence

import bps.budget.model.BudgetData
import java.util.UUID

interface BudgetDao : AutoCloseable {

    val userBudgetDao: UserBudgetDao get() = TODO()
    val transactionDao: TransactionDao get() = TODO()
    val accountDao: AccountDao get() = TODO()

    fun prepForFirstLoad() {}

    /**
     * @throws DataConfigurationException if data isn't found.
     */
    fun load(budgetId: UUID, userId: UUID): BudgetData = TODO()

    override fun close() {}

}

