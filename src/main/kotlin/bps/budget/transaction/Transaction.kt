package bps.budget.transaction

import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount

interface Transaction {
    val amount: Double
    val category: CategoryAccount
    val real: RealAccount
    fun commit() {
        category.commit(this)
        real.commit(this)
    }

    companion object {

        operator fun invoke(
            amount: Double,
            category: CategoryAccount,
            real: RealAccount,
        ): Transaction = object : Transaction {
            override val amount: Double = amount
            override val category: CategoryAccount = category
            override val real: RealAccount = real
        }

    }
}
