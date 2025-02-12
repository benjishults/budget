package bps.budget.jdbc

import bps.budget.analytics.AnalyticsOptions
import bps.budget.model.CategoryAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AnalyticsDao
import bps.budget.persistence.jdbc.JdbcAnalyticsDao
import bps.kotlin.WithMockClock
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp

class AnalyticsDaoTest : FreeSpec(),
    WithMockClock {

    init {
        val timeZone: TimeZone = TimeZone.of("America/New_York")
        // NOTE midnight in NY
        val now = Instant.parse("2024-08-01T04:00:00Z")
        val clock = produceSecondTickingClock(now)
        // NOTE midnight in NY
        val pastClock = produceDayTickingClock(Instant.parse("2023-08-01T04:00:00.500Z"))

        val connection: Connection = mockk(relaxed = true)
        val accountDao: AccountDao = mockk(relaxed = true)
        val dao: AnalyticsDao = JdbcAnalyticsDao(
            connection = connection,
            accountDao = accountDao,
            // NOTE what gets passed in here doesn't matter since we are mocking the results on the connection
            clock = clock,
        )
        val preparedStatement: PreparedStatement = mockk(relaxed = true)
        val resultSet: ResultSet = mockk(relaxed = true)

        every { connection.prepareStatement(any()) } answers {
            preparedStatement
        }
        every { preparedStatement.executeQuery() } answers {
            resultSet
        }
        // NOTE needs to be big enough to cover one expenditure per day for the 13 months between the pastClock and clock
        val numberOfExpenditures = 366
        val timestampList = buildList {
            var timestamp = Timestamp.from(pastClock.now().toJavaInstant())
            repeat(numberOfExpenditures) {
                add(timestamp)
                timestamp = Timestamp.from(pastClock.now().toJavaInstant())
            }
        }
        every { resultSet.next() } returnsMany
                buildList { repeat(timestampList.size) { add(true) } } andThen
                false
        every { resultSet.getTimestamp(any<String>()) } returnsMany
                timestampList
        // NOTE spending $50 every day
        every { resultSet.getBigDecimal("amount") } returnsMany
                buildList {
                    repeat(timestampList.size) {
                        add((-50).toBigDecimal())
                    }
                }
        "test averages" {
            val foodAccount: CategoryAccount = mockk(relaxed = true)
            dao.averageExpenditure(
                foodAccount,
                timeZone,
                // NOTE this doesn't really matter since we're mocking the results
                AnalyticsOptions(
                    excludeFutureUnits = true,
                    excludeCurrentUnit = true,
                    excludePreviousUnit = true,
                    // NOTE this is midnight in New York (though it's ignored due to mocking the connection)
                    since = Instant.parse("2023-08-01T04:00:00Z"),
                ),
            ) shouldBe (366 * 50 / 12).toBigDecimal()
        }
    }

}
