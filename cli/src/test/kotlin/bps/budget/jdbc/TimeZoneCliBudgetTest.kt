package bps.budget.jdbc

import bps.budget.jdbc.test.JdbcCliBudgetTestFixture
import bps.budget.jdbc.test.dropTables
import bps.jdbc.HikariYamlConfig
import bps.jdbc.JdbcConfig
import bps.jdbc.JdbcFixture
import bps.jdbc.JdbcFixture.Companion.transactOrThrow
import bps.jdbc.getConfigFromResource
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.sql.Timestamp
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TimeZoneCliBudgetTest : FreeSpec(),
    JdbcFixture {

    val jdbcConfig: JdbcConfig
    val hikariConfig: HikariYamlConfig

    init {
        getConfigFromResource("noDataJdbc.yml")
            .also {
                jdbcConfig = it.first
                hikariConfig = it.second
            }
    }

    val budgetName: String = "${this::class.simpleName!!}-${Uuid.random()}"

    init {
        val jdbcCliBudgetTestFixture =
            JdbcCliBudgetTestFixture(jdbcConfig, budgetName)
        with(jdbcCliBudgetTestFixture) {
            beforeSpec {
                dropTables(dataSource, jdbcConfig.schema)
            }
            afterSpec {
                dropTables(dataSource, jdbcConfig.schema)
            }
            "create table" - {
                dataSource.transactOrThrow {
                    createStatement().use { statement ->
                        statement.execute(
                            """
                        create table timestamps
                        (
                            timestamp_utc           timestamp                not null,
                            timestamp_with_timezone timestamp with time zone not null,
                            label                   varchar(100)             not null
                        )
                        """.trimIndent(),
                        )
                    }
                }
                "insert timestamps with bps.jdbc.JdbcFixture.setTimestamp" - {
                    val now = Instant.parse("2024-08-09T00:00:00.00Z")
                    val nowAmericaChicago = "2024-08-08T19:00"
                    val label2 = "basic test"
                    dataSource.transactOrThrow {
                        prepareStatement(
                            """
                        insert into timestamps (timestamp_utc, timestamp_with_timezone, label)
                        values (?, ?, ?)
                        """.trimIndent(),
                        )
                            .use { statement ->
                                statement.setInstant(1, now)
                                statement.setInstant(2, now)
                                statement.setString(3, label2)
                                statement.executeUpdate()
                            }
                    }
                    "read timestamps with bps.budget.persistence.jdbc.JdbcDaoKt.toLocalDateTime(java.sql.Timestamp, java.util.TimeZone) and validate" {
                        dataSource.transactOrThrow {
                            prepareStatement("select timestamp_utc, timestamp_with_timezone from timestamps where label = ?")
                                .use { statement ->
                                    statement.setString(1, label2)
                                    statement.executeQuery()
                                        .use { resultSet ->
                                            resultSet.next()
                                            val timestampUtc: Timestamp = resultSet.getTimestamp("timestamp_utc")
                                            val timestampWithTimezone: Timestamp =
                                                resultSet.getTimestamp("timestamp_with_timezone")
                                            timestampUtc.toString() shouldBe timestampWithTimezone.toString()
                                            println("$label2: $timestampUtc")
                                            val localDateTime = timestampUtc
                                                .toLocalDateTime(TimeZone.of("America/Chicago"))
                                            localDateTime shouldBe now.toLocalDateTime(TimeZone.of("America/Chicago"))
                                            localDateTime.toString() shouldStartWith nowAmericaChicago
                                        }
                                }
                        }
                    }
                }
            }
        }
    }

}
