package bps.budget.persistence.jdbc

import bps.budget.auth.UserBudgetAccess
import bps.budget.auth.CoarseAccess
import bps.budget.auth.AuthenticatedUser
import bps.budget.auth.User
import bps.budget.persistence.UserBudgetDao
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import bps.kotlin.Instrumentable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

@Instrumentable
class JdbcUserBudgetDao(
    val connection: Connection,
    val errorStateTracker: JdbcDao.ErrorStateTracker,
) : UserBudgetDao, JdbcFixture {

    override fun getUserByLoginOrNull(login: String): User? =
        connection.transactOrThrow {
            prepareStatement(
                """
                |select *
                |from users u
                |  left join budget_access ba on u.id = ba.user_id
                |where u.login = ?
                """.trimMargin(),
            )
                .use { preparedStatement: PreparedStatement ->
                    preparedStatement.setString(1, login)
                    preparedStatement.executeQuery()
                        .use { resultSet: ResultSet ->
                            if (resultSet.next()) {
                                val budgets = mutableListOf<UserBudgetAccess>()
                                val id = resultSet.getObject("id", UUID::class.java)
                                do {
                                    val budgetName: String? = resultSet.getString("budget_name")
                                    if (budgetName !== null)
                                        budgets.add(
                                            UserBudgetAccess(
                                                budgetId = resultSet.getObject("budget_id", UUID::class.java),
                                                budgetName = budgetName,
                                                timeZone = resultSet.getString("time_zone")
                                                    ?.let { timeZone -> TimeZone.of(timeZone) }
                                                    ?: TimeZone.currentSystemDefault(),
                                                analyticsStart = resultSet.getInstant("analytics_start"),
                                                coarseAccess = resultSet.getString("coarse_access")
                                                    ?.let(CoarseAccess::valueOf),
                                            ),
                                        )
                                } while (resultSet.next())
                                // NOTE not doing authentication yet.
                                AuthenticatedUser(id, login, budgets)
                            } else
                                null
                        }
                }
        }

    override fun createUser(login: String, password: String, userId: UUID): UUID =
        userId.also {
            errorStateTracker.catchCommitErrorState {
                connection.transactOrThrow {
                    prepareStatement("insert into users (login, id) values (?, ?)")
                        .use { statement ->
                            statement.setString(1, login)
                            statement.setUuid(2, userId)
                            statement.executeUpdate()
                        }
                }
            }
        }

    override fun grantAccess(
        budgetName: String,
        timeZoneId: String,
        analyticsStart: Instant,
        userId: UUID,
        budgetId: UUID,
    ) {
        connection.transactOrThrow {
            prepareStatement(
                """
                    insert into budget_access (id, budget_id, user_id, time_zone, analytics_start, budget_name)
                    values (?, ?, ?, ?, ?, ?) on conflict do nothing
                """.trimIndent(),
            )
                .use { createBudgetStatement: PreparedStatement ->
                    createBudgetStatement.setUuid(1, UUID.randomUUID())
                    createBudgetStatement.setUuid(2, budgetId)
                    createBudgetStatement.setUuid(3, userId)
                    createBudgetStatement.setString(4, timeZoneId)
                    createBudgetStatement.setInstant(5, analyticsStart)
                    createBudgetStatement.setString(6, budgetName)
                    createBudgetStatement.executeUpdate()
                }
        }
    }

    override fun updateTimeZone(timeZoneId: String, userId: UUID, budgetId: UUID): Int =
        connection.transactOrThrow {
            prepareStatement(
                """
                    update budget_access
                    set time_zone = ?
                    where user_id = ?
                      and budget_id = ?
                """.trimIndent(),
            )
                .use { updateTimeZoneStatement: PreparedStatement ->
                    updateTimeZoneStatement.setString(1, timeZoneId)
                    updateTimeZoneStatement.setUuid(2, userId)
                    updateTimeZoneStatement.setUuid(3, budgetId)
                    updateTimeZoneStatement.executeUpdate()
                }
        }

    override fun updateAnalyticsStart(analyticsStart: Instant, userId: UUID, budgetId: UUID): Int =
        connection.transactOrThrow {
            prepareStatement(
                """
                    update budget_access
                    set analytics_start = ?
                    where user_id = ?
                      and budget_id = ?
                """.trimIndent(),
            )
                .use { updateTimeZoneStatement: PreparedStatement ->
                    updateTimeZoneStatement.setInstant(1, analyticsStart)
                    updateTimeZoneStatement.setUuid(2, userId)
                    updateTimeZoneStatement.setUuid(3, budgetId)
                    updateTimeZoneStatement.executeUpdate()
                }
        }

    override fun createBudgetOrNull(generalAccountId: UUID, budgetId: UUID): UUID? =
        connection.transactOrThrow {
            prepareStatement(
                """
                insert into budgets (id, general_account_id)
                values (?, ?) on conflict do nothing""",
            )
                .use { createBudgetStatement: PreparedStatement ->
                    createBudgetStatement.setUuid(1, budgetId)
                    createBudgetStatement.setUuid(2, generalAccountId)
                    if (createBudgetStatement.executeUpdate() != 1)
                        null
                    else
                        budgetId
                }
        }

    override fun deleteUser(userId: UUID) {
        errorStateTracker.catchCommitErrorState {
            connection.transactOrThrow {
                prepareStatement("delete from budget_access where user_id = ?")
                    .use { statement: PreparedStatement ->
                        statement.setUuid(1, userId)
                        statement.executeUpdate()
                    }
                prepareStatement("delete from users where id = ?")
                    .use { statement: PreparedStatement ->
                        statement.setUuid(1, userId)
                        statement.executeUpdate()
                    }
            }
        }
    }


    override fun deleteUserByLogin(login: String) {
        errorStateTracker.catchCommitErrorState {
            connection.transactOrThrow {
                getUserIdByLogin(login)
                    ?.let { userId: UUID ->
                        prepareStatement("delete from budget_access where user_id = ?")
                            .use { statement: PreparedStatement ->
                                statement.setUuid(1, userId)
                                statement.executeUpdate()
                            }
                        prepareStatement("delete from users where login = ?")
                            .use { statement: PreparedStatement ->
                                statement.setString(1, login)
                                statement.executeUpdate()
                            }
                    }
            }
        }
    }

    private fun Connection.getUserIdByLogin(login: String): UUID? =
        prepareStatement("select id from users where login = ?")
            .use { statement: PreparedStatement ->
                statement.setString(1, login)
                statement.executeQuery()
                    .use { resultSet: ResultSet ->
                        if (resultSet.next()) {
                            resultSet.getObject("id", UUID::class.java)
                        } else
                            null
                    }
            }

    override fun deleteBudget(budgetId: UUID) {
        errorStateTracker.catchCommitErrorState {
            connection.transactOrThrow {
                prepareStatement("delete from budget_access where budget_id = ?")
                    .use { statement: PreparedStatement ->
                        statement.setUuid(1, budgetId)
                        statement.executeUpdate()
                    }
                prepareStatement("delete from budgets where id = ?")
                    .use { statement: PreparedStatement ->
                        statement.setUuid(1, budgetId)
                        statement.executeUpdate()
                    }
            }
        }
    }

}
