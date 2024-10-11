package bps.budget.persistence.migration

import bps.budget.BudgetConfigurations
import bps.budget.persistence.jdbc.JdbcDao
import bps.config.convertToPath
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import java.sql.Connection
import java.util.UUID

class DataMigrations {

    companion object : JdbcFixture {
        @JvmStatic
        fun main(args: Array<String>) {
            val argsList: List<String> = args.toList()
            val typeIndex = argsList.indexOfFirst { it == "-type" } + 1
            if ("-type" !in argsList || argsList.size <= typeIndex) {
                println("Usage: java DataMigrations -type <type> [-schema <schema>]")
                println("Migration types: new-account_active_periods-table")
            } else {
                val configurations =
                    BudgetConfigurations(
                        sequenceOf(
                            "migrations.yml",
                            convertToPath("~/.config/bps-budget/migrations.yml"),
                        ),
                    )
                val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)
                with(jdbcDao.connection) {
                    jdbcDao.use {
                        try {
                            transactOrThrow {
                                prepareStatement(
                                    """
                            select ba.budget_id
                            from users u
                            join budget_access ba on u.id = ba.user_id
                            where ba.budget_name = ?
                            and u.login = ?
                            """.trimIndent(),
                                )
                                    .use { statement ->
                                        statement.setString(1, configurations.persistence.jdbc!!.budgetName)
                                        statement.setString(2, configurations.user.defaultLogin)
                                        statement.executeQuery().use { resultSet ->
                                            resultSet.next()
                                            val budgetId: UUID = resultSet.getUuid("budget_id")
                                            migrateAccountsToActivityPeriodTable("category", budgetId)
                                            migrateAccountsToActivityPeriodTable("real", budgetId)
                                            migrateAccountsToActivityPeriodTable("charge", budgetId)
                                            migrateAccountsToActivityPeriodTable("draft", budgetId)
                                        }
                                    }
                            }
                        } catch (ex: Throwable) {
                            ex.printStackTrace()
                        }
                    }
                }
            }
        }

        private fun Connection.migrateAccountsToActivityPeriodTable(tablePrefix: String, budgetId: UUID) {
            buildList {
                prepareStatement("select id from ${tablePrefix}_accounts where budget_id = ?")
                    .use { statement ->
                        statement.setUuid(1, budgetId)
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                add(resultSet.getUuid("id"))
                            }
                        }
                    }
            }
                .let { accountIds: List<UUID> ->
                    accountIds.forEach { accountId ->
                        prepareStatement(
                            """
                    insert into account_active_periods (id, ${tablePrefix}_account_id, budget_id)
                    values (?, ?, ?)
                    on conflict do nothing
                        """.trimIndent(),
                        )
                            .use { statement ->
                                statement.setUuid(1, UUID.randomUUID())
                                statement.setUuid(2, accountId)
                                statement.setUuid(3, budgetId)
                                statement.executeUpdate()
                            }

                    }
                }
        }
    }

}

