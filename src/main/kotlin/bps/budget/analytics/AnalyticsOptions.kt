package bps.budget.analytics

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime

interface AnalyticsOptions {

    //        val excludeFirstActiveUnit: Boolean
    val excludeFutureUnits: Boolean
    val excludeCurrentUnit: Boolean

    //        val excludeMaxAndMinFromAverage: Boolean
//        val minimumUnitsAfterExclusions: Int
//        val timeUnit: DateTimeUnit
    val since: Instant
//    val hardBeginning: LocalDateTime

    companion object {
        operator fun invoke(
//                excludeFirstActiveUnit: Boolean,
//                excludeMaxAndMinFromAverage: Boolean,
//                minimumUnitsAfterExclusions: Int,
//                timeUnit: DateTimeUnit,
            excludeFutureUnits: Boolean,
            excludeCurrentUnit: Boolean,
            since: Instant,
//            hardBeginning: LocalDateTime,
        ): AnalyticsOptions =
            object : AnalyticsOptions {
                //                    override val excludeFirstActiveUnit = excludeFirstActiveUnit
//                    override val excludeMaxAndMinFromAverage = excludeMaxAndMinFromAverage
//                    override val minimumUnitsAfterExclusions = minimumUnitsAfterExclusions
//                    override val timeUnit = timeUnit
                override val excludeFutureUnits: Boolean = excludeFutureUnits
                override val excludeCurrentUnit: Boolean = excludeCurrentUnit
                override val since: Instant = since
//                override val hardBeginning = hardBeginning

                init {
                    require(excludeFutureUnits || !excludeCurrentUnit)
                }

            }
    }

}
