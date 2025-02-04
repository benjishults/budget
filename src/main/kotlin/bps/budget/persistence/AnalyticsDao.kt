package bps.budget.persistence

import bps.budget.analytics.AnalyticsOptions
import bps.budget.model.CategoryAccount
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

    fun averageExpenditure(
        categoryAccount: CategoryAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions,
    ): BigDecimal? =
        TODO()

    fun maxExpenditure(): BigDecimal? =
        TODO("Not yet implemented")

    fun minExpenditure(): BigDecimal? =
        TODO("Not yet implemented")

}
