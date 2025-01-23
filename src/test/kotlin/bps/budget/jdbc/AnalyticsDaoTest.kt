package bps.budget.jdbc

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
        val now = Instant.parse("2024-08-09T00:00:00.500Z")
        val clock = produceSecondTickingClock(now)
        val pastClock = produceDayTickingClock(Instant.parse("2023-07-09T00:00:00.500Z"))

        val connection: Connection = mockk(relaxed = true)
        val accountDao: AccountDao = mockk(relaxed = true)
        val dao: AnalyticsDao = JdbcAnalyticsDao(
            connection = connection,
            accountDao = accountDao,
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
        val numberOfExpenditures = 400
        val timestampList = buildList {
            var timestamp = Timestamp.from(pastClock.now().toJavaInstant())
            val timestampOfNow = Timestamp.from(now.toJavaInstant())
            repeat(numberOfExpenditures) {
                if (timestamp.before(timestampOfNow))
                    add(timestamp)
                timestamp = Timestamp.from(pastClock.now().toJavaInstant())
            }
        }
        every { resultSet.next() } returnsMany
                buildList { repeat(timestampList.size) { add(true) } } andThen
                false
        every { resultSet.getTimestamp(any<String>()) } returnsMany
                timestampList
        every { resultSet.getBigDecimal("amount") } returnsMany
                buildList {
                    repeat(timestampList.size) {
                        add((-50).toBigDecimal())
                    }
                }
        "test averages" {
            val foodAccount: CategoryAccount = mockk(relaxed = true)
            // NOTE this happens by a bizarre coincidence.  While you would expect the answer to be quite close to
            //    this value, it's just lucky that it is exact.
            dao.averageExpenditure(
                foodAccount,
                timeZone,
            ) shouldBe (50 * 30).toBigDecimal()
        }
    }

}
