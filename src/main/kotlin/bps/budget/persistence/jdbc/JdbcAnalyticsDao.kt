package bps.budget.persistence.jdbc

import bps.budget.analytics.AnalyticsOptions
import bps.budget.model.CategoryAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AnalyticsDao
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import bps.time.NaturalLocalInterval
import bps.time.atStartOfMonth
import bps.time.naturalMonthInterval
import kotlinx.datetime.Clock
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
) : AnalyticsDao, JdbcFixture {

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
            expenditures.compute(expenditure.timestamp.atStartOfMonth()) { key: LocalDateTime, foundValue: ExpendituresInInterval? ->
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

    // TODO make a single function that returns various analytics to avoid multiple trips to the DB.
    //      I imagine each of these analytics functions will be pulling the same data.
    override fun averageExpenditure(
        categoryAccount: CategoryAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions,
    ): BigDecimal? =
        connection.transactOrThrow {
            val expenditures = MonthlyExpenditureSeries()
            prepareStatement(
                """
                |select t.timestamp_utc, ti.amount from transaction_items ti
                |join transactions t
                |  on ti.transaction_id = t.id
                |    and ti.budget_id = t.budget_id
                |where ti.account_id = ?
                |  and t.type = 'expense'
                |  and ti.amount < 0
                |  and t.timestamp_utc >= ?
                |  ${if (options.excludeCurrentUnit || options.excludeFutureUnits) "and t.timestamp_utc < ?" else ""}
                |  and ti.budget_id = ?
                |order by t.timestamp_utc asc
            """.trimMargin(),
                // TODO page this if we run into DB latency
//                |offset ?
//                |limit 100
            )
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, categoryAccount.id)
                    val sinceTimestamp: Timestamp = Timestamp.from(options.since.toJavaInstant())
                    statement.setTimestamp(2, sinceTimestamp)
                    if (options.excludeCurrentUnit) {
                        statement.setInstant(
                            3,
                            clock
                                .now()
                                .atStartOfMonth(timeZone)
                                // NOTE safe because it is midnight and offsets generally occur at or after
                                //      1 a.m. local time
                                .toInstant(timeZone),
                        )
                        statement.setUuid(4, categoryAccount.budgetId)
                    } else if (options.excludeFutureUnits) {
                        statement.setInstant(3, clock.now())
                        statement.setUuid(4, categoryAccount.budgetId)
                    } else {
                        statement.setUuid(3, categoryAccount.budgetId)
                    }
                    statement.executeQuery()
                        .use { resultSet: ResultSet ->
                            while (resultSet.next()) {
                                expenditures.add(
                                    Expenditure(
                                        -resultSet.getBigDecimal("amount"),
                                        resultSet.getInstantOrNull()!!
                                            // FIXME do these really need to be LocalDateTimes?
                                            //       if not, we may avoid some problems my leaving them as Instants
                                            .toLocalDateTime(timeZone),
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

