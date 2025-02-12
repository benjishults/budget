package bps.budget.persistence

import bps.budget.analytics.AnalyticsOptions
import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID

interface AnalyticsDao {

    val clock: Clock

    fun averageIncome(
        timeZone: TimeZone,
        options: AnalyticsOptions,
        budgetId: UUID,
    ): BigDecimal? =
        TODO()

    fun averageIncome(
        realAccount: RealAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions,
        budgetId: UUID,
    ): BigDecimal? =
        TODO()

    fun maxIncome(): BigDecimal? =
        TODO("Not yet implemented")

    fun minIncome(): BigDecimal? =
        TODO("Not yet implemented")

    fun averageExpenditure(
        categoryAccount: CategoryAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions,
    ): BigDecimal? =
        TODO()

    fun averageExpenditure(
        timeZone: TimeZone,
        options: AnalyticsOptions,
        budgetId: UUID,
    ): BigDecimal? =
        TODO()

    fun maxExpenditure(): BigDecimal? =
        TODO("Not yet implemented")

    fun minExpenditure(): BigDecimal? =
        TODO("Not yet implemented")

}
