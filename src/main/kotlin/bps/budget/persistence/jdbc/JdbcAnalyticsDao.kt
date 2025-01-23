package bps.budget.persistence.jdbc

import bps.budget.model.CategoryAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AnalyticsDao
import bps.budget.persistence.migration.DataMigrations.Companion.getLocalDateTimeForTimeZone
import bps.budget.persistence.migration.DataMigrations.Companion.setUuid
import bps.jdbc.transactOrThrow
import bps.time.NaturalLocalInterval
import bps.time.NaturalMonthLocalInterval
import bps.time.naturalMonthInterval
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.SortedMap
import java.util.TreeMap

class JdbcAnalyticsDao(
    val connection: Connection,
    val accountDao: AccountDao,
    override val clock: Clock = Clock.System,
) : AnalyticsDao {

    data class Expenditure(
        val amount: BigDecimal,
        val timestamp: LocalDateTime,
    ) {
        init {
            require(amount > BigDecimal.ZERO) { "amount must be positive" }
        }
    }

    class ExpendituresInInterval(
        val interval: NaturalLocalInterval,
    ) : Comparable<ExpendituresInInterval> {

        private val _expenditures = mutableListOf<Expenditure>()
        val expenditures: List<Expenditure>
            get() = _expenditures.toList()

        fun add(expenditure: Expenditure) {
            _expenditures.add(expenditure)
        }

        override fun compareTo(other: ExpendituresInInterval): Int =
            interval.start.compareTo(other.interval.start)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExpendituresInInterval) return false

            if (interval != other.interval) return false

            return true
        }

        override fun hashCode(): Int {
            return interval.hashCode()
        }

    }

    open class ExpenditureSeries<K : Comparable<K>>(
        open val expenditures: SortedMap<K, ExpendituresInInterval>,
    ) {
//        init {
//            require(
//                expenditures
//                    .keys
//                    .asSequence()
//                    .drop(1)
//                    .runningFold(expenditures.keys.first()) { a: NaturalLocalInterval, b: NaturalLocalInterval ->
//                        a.start + a.unit == b.start
//                    }
//                    .all { it },
//            )
//        }
    }

    class MonthlyExpenditureSeries(
        expenditures: SortedMap<LocalDateTime, ExpendituresInInterval> = TreeMap(),
    ) : ExpenditureSeries<LocalDateTime>(expenditures) {
//        init {
//            var previous: NaturalMonthLocalInterval = expenditures.keys.first()
//            require(
//                expenditures
//                    .keys
//                    .asSequence()
//                    .drop(1)
//                    .map { current: NaturalMonthLocalInterval ->
//                        (previous.start.monthNumber % 12 == current.start.monthNumber - 1)
//                            .also {
//                                previous = current
//                            }
//                    }
//                    .all { it },
//            )
//        }

        fun add(expenditure: Expenditure) {
            expenditures.compute(expenditure.timestamp.naturalMonthInterval().start) { key: LocalDateTime, foundValue: ExpendituresInInterval? ->
                (foundValue ?: ExpendituresInInterval(key.naturalMonthInterval()))
                    .also {
                        it.add(expenditure)
                    }
            }
        }

        // TODO worry about options
        // TODO worry about exceptions
        fun average(options: AnalyticsOptions): BigDecimal? =
            computeMonthlyTotals(options)
                .let { listOfTotals: List<BigDecimal> ->
                    if (listOfTotals.isEmpty())
                        null
                    else
                        listOfTotals.reduce(BigDecimal::plus) / listOfTotals.size.toBigDecimal()
                }

        // TODO worry about options
        // TODO worry about exceptions
        fun max(options: AnalyticsOptions): BigDecimal? =
            computeMonthlyTotals(options)
                .let { listOfTotals: List<BigDecimal> ->
                    if (listOfTotals.isEmpty())
                        null
                    else
                        listOfTotals.max()
                }

        // TODO worry about options
        // TODO worry about exceptions
        fun min(options: AnalyticsOptions): BigDecimal? =
            computeMonthlyTotals(options)
                .let { listOfTotals: List<BigDecimal> ->
                    if (listOfTotals.isEmpty())
                        null
                    else
                        listOfTotals.min()
                }

        // TODO worry about options
        // TODO worry about exceptions
        private fun computeMonthlyTotals(options: AnalyticsOptions): List<BigDecimal> =
            expenditures
                .map { (_: LocalDateTime, v: ExpendituresInInterval) ->
                    v.expenditures
                        .map { it.amount }
                        .reduce(BigDecimal::plus)
                }

    }

    override fun averageExpenditure(
        categoryAccount: CategoryAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions,
    ): BigDecimal? =
        connection.transactOrThrow {
            val expenditures = MonthlyExpenditureSeries()
            // TODO
            //    1. find active units
            //    2. determine whether to include first
            //    3. find totals in each unit
            //    4. make exclusions as required by options
            //    5. calculate average expenditure per unit
            prepareStatement(
                """
                |select t.timestamp_utc, ti.amount from transaction_items ti
                |join transactions t
                |on ti.transaction_id = t.id
                |and ti.budget_id = t.budget_id
                |where ti.account_id = ?
                |and ti.amount < 0 -- expenditures only
                |and t.timestamp_utc >= ?
                |${if (options.excludeFutureUnits) "and t.timestamp_utc < now()" else ""}
                |and ti.budget_id = ?
                |order by t.timestamp_utc asc
            """.trimMargin(),
//                |offset ?
//                |limit 100
            )
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, categoryAccount.id)
                    val timestampAtStartOfSinceMonth: Timestamp =
                        Timestamp.from(
                            options.since
                                .toLocalDateTime(timeZone)
                                .naturalMonthInterval()
                                .start
                                .toInstant(timeZone)
                                .toJavaInstant(),
                        )
                    statement.setTimestamp(2, timestampAtStartOfSinceMonth)
                    statement.setUuid(3, categoryAccount.budgetId)
                    statement.executeQuery()
                        .use { resultSet: ResultSet ->
                            val filter: (LocalDateTime) -> Boolean =
                                if (options.excludeCurrentUnit) {
                                    val thisMonth: NaturalMonthLocalInterval =
                                        clock.now().toLocalDateTime(timeZone).naturalMonthInterval()

                                    fun(t: LocalDateTime) = t < thisMonth.start
                                } else {
                                    { true }
                                }
                            while (resultSet.next()) {
                                val timestamp = resultSet.getLocalDateTimeForTimeZone(timeZone)
                                if (filter(timestamp))
                                    expenditures.add(
                                        Expenditure(
                                            -resultSet.getBigDecimal("amount"),
                                            timestamp,
                                        ),
                                    )
                            }
                        }
                }
            expenditures.average(options)
        }

    // FIXME
    override fun maxExpenditure(): BigDecimal? =
        null

    // FIXME
    override fun minExpenditure(): BigDecimal? =
        null

}

interface AnalyticsOptions {

    //        val excludeFirstActiveUnit: Boolean
    val excludeFutureUnits: Boolean
    val excludeCurrentUnit: Boolean

    //        val excludeMaxAndMinFromAverage: Boolean
//        val minimumUnitsAfterExclusions: Int
//        val timeUnit: DateTimeUnit
    val since: Instant

    companion object {
        operator fun invoke(
//                excludeFirstActiveUnit: Boolean,
//                excludeMaxAndMinFromAverage: Boolean,
//                minimumUnitsAfterExclusions: Int,
//                timeUnit: DateTimeUnit,
            excludeFutureUnits: Boolean,
            excludeCurrentUnit: Boolean,
            since: Instant,
        ): AnalyticsOptions =
            object : AnalyticsOptions {
                //                    override val excludeFirstActiveUnit = excludeFirstActiveUnit
//                    override val excludeMaxAndMinFromAverage = excludeMaxAndMinFromAverage
//                    override val minimumUnitsAfterExclusions = minimumUnitsAfterExclusions
//                    override val timeUnit = timeUnit
                override val excludeFutureUnits: Boolean = excludeFutureUnits
                override val excludeCurrentUnit: Boolean = excludeCurrentUnit
                override val since: Instant = since

                init {
                    require(excludeFutureUnits || !excludeCurrentUnit)
                }

            }
    }

}
