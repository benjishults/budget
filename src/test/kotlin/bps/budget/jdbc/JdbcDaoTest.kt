package bps.budget.jdbc

import bps.budget.model.CategoryAccount
import bps.budget.persistence.jdbc.JdbcConfig
import bps.budget.persistence.jdbc.JdbcDao
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class JdbcDaoTest : FreeSpec() {
    init {

        val timestampFactory: () -> Timestamp = object : () -> Timestamp {
            var secondCount = 0
            override operator fun invoke(): Timestamp =
                Timestamp.from(java.time.Instant.parse(String.format("2024-08-09T00:00:%02d.500Z", secondCount++)))
        }

        val connection = mockDriverManagerAndConnection()

        val jdbcDaoUnderTest = JdbcDao(
            config = JdbcConfig(),
        )
        afterSpec {
            jdbcDaoUnderTest.close()
            unmockkAll()
        }

        val budgetId = UUID.randomUUID()
        "test fetchTransactionItemsInvolvingAccount" {
            val preparedStatement = mockk<PreparedStatement>(relaxed = true)
            val resultSet = mockk<ResultSet>(relaxed = true)

            every { connection.prepareStatement(any()) } returns
                    preparedStatement
            every { preparedStatement.executeQuery() } returns
                    resultSet

            every { resultSet.next() } returns
                    true andThen
                    true andThen
                    true andThen
                    false
            with(jdbcDaoUnderTest) {
                every { resultSet.getString(match<String> { it.endsWith("description") }) } returns
                        "description"
                every { resultSet.getString("draft_status") } returns
                        "none"
                every { resultSet.getString("type") } returns
                        "expense"
                every { resultSet.getUuid(any()) } answers
                        { UUID.randomUUID() }
                every { resultSet.getUuid("budget_id") } returns
                        budgetId
                every { resultSet.getTimestamp(any<String>()) } answers
                        { timestampFactory() }
                val initialAmount = 5.toBigDecimal().setScale(2)
                var amount = initialAmount
                every { resultSet.getCurrencyAmount("amount") } returns
                        amount++ andThen
                        amount++ andThen
                        amount++ andThen
                        amount++ andThen
                        amount++ andThen
                        amount++ andThen
                        amount
                val account = CategoryAccount(
                    name = "stuff",
                    description = "stuff",
                    id = UUID.randomUUID(),
                    balance = 1000.toBigDecimal().setScale(2),
                    budgetId = budgetId,
                )
                val extendedTransactionItems = transactionDao.fetchTransactionItemsInvolvingAccount(account, 3)
                    .sorted()
                withClue("extendedTransactionItems size") { extendedTransactionItems.size shouldBe 3 }
                extendedTransactionItems[0].asClue {
                    it.accountBalanceAfterItem shouldBe
                            1000.toBigDecimal().setScale(2)
                    it.amount shouldBe
                            5.toBigDecimal().setScale(2)
                }
                extendedTransactionItems[1].asClue {
                    it.accountBalanceAfterItem shouldBe
                            995.toBigDecimal().setScale(2)
                    it.amount shouldBe
                            6.toBigDecimal().setScale(2)
                }
                extendedTransactionItems[2].asClue {
                    it.accountBalanceAfterItem shouldBe
                            (995 - 6).toBigDecimal().setScale(2)
                    it.amount shouldBe
                            7.toBigDecimal().setScale(2)
                }
                every { resultSet.next() } returns
                        true andThen
                        true andThen
                        true andThen
                        false
                shouldThrow<IllegalArgumentException> {
                    transactionDao.fetchTransactionItemsInvolvingAccount(
                        account,
                        3,
                        3,
                    )
                }
                val secondPage = transactionDao.fetchTransactionItemsInvolvingAccount(
                    account,
                    3,
                    3,
                    (995 - (6 + 7)).toBigDecimal().setScale(2),
                )
                    .sorted()
                withClue("secondPage size") { secondPage.size shouldBe 3 }
                secondPage[0].asClue {
                    it.accountBalanceAfterItem shouldBe
                            (995 - (6 + 7)).toBigDecimal().setScale(2)
                    it.amount shouldBe
                            8.toBigDecimal().setScale(2)
                }
                secondPage[1].asClue {
                    it.accountBalanceAfterItem shouldBe
                            (995 - (6 + 7 + 8)).toBigDecimal().setScale(2)
                    it.amount shouldBe
                            9.toBigDecimal().setScale(2)
                }
                secondPage[2].asClue {
                    it.accountBalanceAfterItem shouldBe
                            (995 - (6 + 7 + 8 + 9)).toBigDecimal().setScale(2)
                    it.amount shouldBe
                            10.toBigDecimal().setScale(2)
                }
            }

        }
    }

    private fun mockDriverManagerAndConnection(): Connection {
        mockkStatic(DriverManager::class)
        val connection = mockk<Connection>(relaxed = true)
        every {
            DriverManager.getConnection(any(), any(), any())
        } answers {
            connection
        }
        every { connection.isValid(any()) } returns
                true
        return connection
    }
}
