package bps.budget.persistence

import bps.budget.model.CategoryAccount
import bps.budget.persistence.jdbc.AnalyticsOptions
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import kotlin.time.Duration

interface AnalyticsDao {

    val clock: Clock

    fun averageExpenditure(
        categoryAccount: CategoryAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions = AnalyticsOptions(
//            excludeFirstActiveUnit = true,
//            excludeMaxAndMin = false,
//            minimumUnits = 3,
//            timeUnit = DateTimeUnit.MONTH,
            excludeFutureUnits = true,
            excludeCurrentUnit = true,
            since = clock.now() - Duration.parse("P395D"),
        ),
    ): BigDecimal? =
        TODO()

    fun maxExpenditure(): BigDecimal? =
        TODO("Not yet implemented")

    fun minExpenditure(): BigDecimal? =
        TODO("Not yet implemented")

}
