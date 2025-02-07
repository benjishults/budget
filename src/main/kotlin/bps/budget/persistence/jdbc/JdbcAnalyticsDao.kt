package bps.budget.persistence.jdbc

import bps.budget.analytics.AnalyticsOptions
import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AnalyticsDao
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import bps.time.NaturalLocalInterval
import bps.time.atStartOfMonth
import bps.time.naturalMonthInterval
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.SortedMap
import java.util.TreeMap
import java.util.UUID

class JdbcAnalyticsDao(
    val connection: Connection,
    val accountDao: AccountDao,
    override val clock: Clock = Clock.System,
) : AnalyticsDao, JdbcFixture {

    data class Item(
        val amount: BigDecimal,
        val timestamp: LocalDateTime,
    ) {
        init {
            require(amount > BigDecimal.ZERO) { "amount must be positive" }
        }
    }

    class ItemsInInterval(
        val interval: NaturalLocalInterval,
    ) : Comparable<ItemsInInterval> {

        private val _items = mutableListOf<Item>()
        val items: List<Item>
            get() = _items.toList()

        fun add(item: Item) {
            _items.add(item)
        }

        override fun compareTo(other: ItemsInInterval): Int =
            interval.start.compareTo(other.interval.start)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ItemsInInterval) return false

            if (interval != other.interval) return false

            return true
        }

        override fun hashCode(): Int {
            return interval.hashCode()
        }

    }

    open class ItemSeries<K : Comparable<K>>(
        open val items: SortedMap<K, ItemsInInterval>,
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

    class MonthlyItemSeries(
        items: SortedMap<LocalDateTime, ItemsInInterval> = TreeMap(),
    ) : ItemSeries<LocalDateTime>(items) {
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

        // TODO this would be less error-prone if we used Instants instead of LocalDateTimes because the start of the month
        //      is usually not an ambiguous LocalDateTime and it would be the only one we would need to do translation on.
        fun add(item: Item) {
            this@MonthlyItemSeries.items.compute(item.timestamp.atStartOfMonth()) { key: LocalDateTime, foundValue: ItemsInInterval? ->
                (foundValue ?: ItemsInInterval(key.naturalMonthInterval()))
                    .also {
                        it.add(item)
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
            this@MonthlyItemSeries.items
                .map { (_: LocalDateTime, v: ItemsInInterval) ->
                    v.items
                        .map { it.amount }
                        .reduce(BigDecimal::plus)
                }

    }

    // FIXME
    override fun maxIncome(): BigDecimal? =
        null

    // FIXME
    override fun minIncome(): BigDecimal? =
        null

    override fun averageIncome(
        timeZone: TimeZone,
        options: AnalyticsOptions,
        budgetId: UUID,
    ): BigDecimal? =
        connection.transactOrThrow {
            val incomes = MonthlyItemSeries()
            prepareStatement(
                """
                |select t.timestamp_utc, ti.amount from transaction_items ti
                |join transactions t
                |  on ti.transaction_id = t.id
                |    and ti.budget_id = t.budget_id
                |where t.type = 'income'
                |  and ti.amount > 0
                |  and t.timestamp_utc >= ?
                |  ${if (options.endDateLimited) "and t.timestamp_utc < ?" else ""}
                |  and ti.budget_id = ?
                |order by t.timestamp_utc asc
            """.trimMargin(),
                // TODO page this if we run into DB latency
//                |offset ?
//                |limit 100
            )
                .use { statement: PreparedStatement ->
                    statement.setInstant(1, options.since)
                    statement.setUuid(
                        setEndTimeStampMaybe(options, statement, 2, this@JdbcAnalyticsDao.clock.now(), timeZone),
                        budgetId,
                    )
                    statement.executeQuery()
                        .use { resultSet: ResultSet ->
                            while (resultSet.next()) {
                                incomes.add(
                                    Item(
                                        resultSet.getBigDecimal("amount"),
                                        resultSet.getInstantOrNull()!!
                                            // FIXME do these really need to be LocalDateTimes?
                                            //       if not, we may avoid some problems my leaving them as Instants
                                            .toLocalDateTime(timeZone),
                                    ),
                                )
                            }
                        }
                }
            incomes.average(options)
        }

    override fun averageIncome(
        realAccount: RealAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions,
        budgetId: UUID,
    ): BigDecimal? =
        connection.transactOrThrow {
            val incomes = MonthlyItemSeries()
            prepareStatement(
                """
                |select t.timestamp_utc, ti.amount from transaction_items ti
                |join transactions t
                |  on ti.transaction_id = t.id
                |    and ti.budget_id = t.budget_id
                |where ti.account_id = ?
                |  and t.type = 'income'
                |  and ti.amount > 0
                |  and t.timestamp_utc >= ?
                |  ${if (options.endDateLimited) "and t.timestamp_utc < ?" else ""}
                |  and ti.budget_id = ?
                |order by t.timestamp_utc asc
            """.trimMargin(),
                // TODO page this if we run into DB latency
//                |offset ?
//                |limit 100
            )
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, realAccount.id)
                    statement.setInstant(2, options.since)
                    statement.setUuid(
                        setEndTimeStampMaybe(options, statement, 3, this@JdbcAnalyticsDao.clock.now(), timeZone),
                        budgetId,
                    )
                    statement.executeQuery()
                        .use { resultSet: ResultSet ->
                            while (resultSet.next()) {
                                incomes.add(
                                    Item(
                                        resultSet.getBigDecimal("amount"),
                                        resultSet.getInstantOrNull()!!
                                            // FIXME do these really need to be LocalDateTimes?
                                            //       if not, we may avoid some problems my leaving them as Instants
                                            .toLocalDateTime(timeZone),
                                    ),
                                )
                            }
                        }
                }
            incomes.average(options)
        }

    override fun averageExpenditure(
        timeZone: TimeZone,
        options: AnalyticsOptions,
        budgetId: UUID,
    ): BigDecimal? =
        connection.transactOrThrow {
            val expenditures = MonthlyItemSeries()
            prepareStatement(
                """
                |select t.timestamp_utc, ti.amount from transaction_items ti
                |join transactions t
                |  on ti.transaction_id = t.id
                |    and ti.budget_id = t.budget_id
                |where t.type = 'expense'
                |  and ti.amount < 0
                |  and t.timestamp_utc >= ?
                |  ${if (options.endDateLimited) "and t.timestamp_utc < ?" else ""}
                |  and ti.budget_id = ?
                |order by t.timestamp_utc asc
            """.trimMargin(),
                // TODO page this if we run into DB latency
//                |offset ?
//                |limit 100
            )
                .use { statement: PreparedStatement ->
                    statement.setInstant(1, options.since)
                    statement.setUuid(
                        setEndTimeStampMaybe(options, statement, 2, this@JdbcAnalyticsDao.clock.now(), timeZone),
                        budgetId,
                    )
                    statement.executeQuery()
                        .use { resultSet: ResultSet ->
                            while (resultSet.next()) {
                                expenditures.add(
                                    Item(
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


    // TODO make a single function that returns various analytics to avoid multiple trips to the DB.
    //      I imagine each of these analytics functions will be pulling the same data.
    override fun averageExpenditure(
        categoryAccount: CategoryAccount,
        timeZone: TimeZone,
        options: AnalyticsOptions,
    ): BigDecimal? =
        connection.transactOrThrow {
            val expenditures = MonthlyItemSeries()
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
                |  ${if (options.endDateLimited) "and t.timestamp_utc < ?" else ""}
                |  and ti.budget_id = ?
                |order by t.timestamp_utc asc
            """.trimMargin(),
                // TODO page this if we run into DB latency
//                |offset ?
//                |limit 100
            )
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, categoryAccount.id)
                    statement.setInstant(2, options.since)
                    statement.setUuid(
                        setEndTimeStampMaybe(options, statement, 3, this@JdbcAnalyticsDao.clock.now(), timeZone),
                        categoryAccount.budgetId,
                    )

                    statement.executeQuery()
                        .use { resultSet: ResultSet ->
                            while (resultSet.next()) {
                                expenditures.add(
                                    Item(
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

    /**
     * Fills in the appropriate [Timestamp] at [statement]'s placeholder [atIndex].  If it filled anything in there,
     * it will return [atIndex]` + 1`.  Otherwise, it returns [atIndex].
     */
    private fun setEndTimeStampMaybe(
        options: AnalyticsOptions,
        statement: PreparedStatement,
        atIndex: Int,
        now: Instant,
        timeZone: TimeZone,
    ): Int =
        if (options.excludePreviousUnit) {
            statement.setInstant(
                atIndex,
                now
                    .atStartOfMonth(timeZone)
                    .let {
                        if (it.month === Month.JANUARY)
                            LocalDateTime(it.year - 1, 12, 1, 0, 0)
                        else
                            LocalDateTime(it.year, it.monthNumber - 1, 1, 0, 0)
                    }
                    // NOTE safe because it is midnight and offsets generally occur at or after
                    //      1 a.m. local time
                    .toInstant(timeZone),
            )
            atIndex + 1
        } else if (options.excludeCurrentUnit) {
            statement.setInstant(
                atIndex,
                now
                    .atStartOfMonth(timeZone)
                    // NOTE safe because it is midnight and offsets generally occur at or after
                    //      1 a.m. local time
                    .toInstant(timeZone),
            )
            atIndex + 1
        } else if (options.excludeFutureTransactions) {
            statement.setInstant(2, now)
            atIndex + 1
        } else {
            atIndex
        }

}

