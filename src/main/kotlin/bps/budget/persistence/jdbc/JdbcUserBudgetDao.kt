package bps.budget.persistence.jdbc

import bps.budget.auth.BudgetAccess
import bps.budget.auth.CoarseAccess
import bps.budget.auth.User
import bps.budget.persistence.UserBudgetDao
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import bps.kotlin.Instrumentable
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

    override fun getUserByLogin(login: String): User? =
        connection.transactOrThrow {
            prepareStatement(
                """
                |select *
                |from users u
                |         left join budget_access ba on u.id = ba.user_id
                |where u.login = ?
                """.trimMargin(),
            )
                .use { preparedStatement: PreparedStatement ->
                    preparedStatement.setString(1, login)
                    preparedStatement.executeQuery()
                        .use { resultSet: ResultSet ->
                            if (resultSet.next()) {
                                val budgets = mutableListOf<BudgetAccess>()
                                val id = resultSet.getObject("id", UUID::class.java)
                                do {
                                    val budgetName: String? = resultSet.getString("budget_name")
                                    if (budgetName !== null)
                                        budgets.add(
                                            BudgetAccess(
                                                budgetId = resultSet.getObject("budget_id", UUID::class.java),
                                                budgetName = budgetName,
                                                timeZone = resultSet.getString("time_zone")
                                                    ?.let { timeZone -> TimeZone.of(timeZone) }
                                                    ?: TimeZone.currentSystemDefault(),
                                                coarseAccess = resultSet.getString("coarse_access")
                                                    ?.let(CoarseAccess::valueOf),
                                            ),
                                        )
                                } while (resultSet.next())
                                User(id, login, budgets)
                            } else
                                null
                        }
                }
        }

    override fun createUser(login: String, password: String): UUID =
        errorStateTracker.catchCommitErrorState {
            UUID.randomUUID()
                .also { uuid: UUID ->
                    connection.transactOrThrow {
                        prepareStatement("insert into users (login, id) values(?, ?)")
                            .use {
                                it.setString(1, login)
                                it.setUuid(2, uuid)
                                it.executeUpdate()
                            }
                    }
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
