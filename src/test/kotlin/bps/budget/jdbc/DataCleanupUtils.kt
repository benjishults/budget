@file:JvmName("DataCleanupUtils")

package bps.budget.jdbc

import bps.jdbc.JdbcFixture
import bps.jdbc.JdbcFixture.Companion.setUuid
import bps.jdbc.transactOrNull
import java.math.BigDecimal
import java.sql.Connection
import java.util.UUID

fun dropTables(connection: Connection, schema: String) {
    require(schema == "clean_after_test")
    connection.transactOrNull {
        createStatement()
            .use { statement ->
                statement.execute("drop table if exists transaction_items")
                statement.execute("drop table if exists transactions")
                statement.execute("drop table if exists account_active_periods")
                statement.execute("drop table if exists staged_draft_accounts")
                statement.execute("drop table if exists staged_real_accounts")
                statement.execute("drop table if exists staged_charge_accounts")
                statement.execute("drop table if exists staged_category_accounts")
                statement.execute("drop table if exists draft_accounts")
                statement.execute("drop table if exists charge_accounts")
                statement.execute("drop table if exists checking_accounts")
                statement.execute("drop table if exists real_accounts")
                statement.execute("drop table if exists category_accounts")
                statement.execute("drop table if exists access_details")
                statement.execute("drop table if exists budget_access")
                statement.execute("drop type if exists coarse_access")
                statement.execute("drop type if exists fine_access")
                statement.execute("drop table if exists budgets")
                statement.execute("drop table if exists users")
                statement.execute("drop table if exists timestamps")
            }
    }
}

fun deleteAccounts(budgetId: UUID, connection: Connection) =
    with(JdbcFixture) {
        cleanupTransactions(budgetId, connection)
        connection.transactOrNull {
            prepareStatement("delete from account_active_periods where budget_id = ?")
                .use {
                    it.setUuid(1, budgetId)
                    it.executeUpdate()
                }
            prepareStatement("delete from draft_accounts where budget_id = ?")
                .use {
                    it.setUuid(1, budgetId)
                    it.executeUpdate()
                }
            prepareStatement("delete from real_accounts where budget_id = ?")
                .use {
                    it.setUuid(1, budgetId)
                    it.executeUpdate()
                }
            prepareStatement("delete from charge_accounts where budget_id = ?")
                .use {
                    it.setUuid(1, budgetId)
                    it.executeUpdate()
                }
            prepareStatement("delete from category_accounts where budget_id = ?")
                .use {
                    it.setUuid(1, budgetId)
                    it.executeUpdate()
                }
        }
    }

fun cleanupTransactions(budgetId: UUID, connection: Connection) =
    with(JdbcFixture) {
        connection.transactOrNull {
            zeroBalance(budgetId, "category_accounts")
            zeroBalance(budgetId, "real_accounts")
            zeroBalance(budgetId, "charge_accounts")
            zeroBalance(budgetId, "draft_accounts")
            prepareStatement("delete from transaction_items where budget_id = ?")
                .use {
                    it.setUuid(1, budgetId)
                    it.executeUpdate()
                }
            prepareStatement("delete from transactions where budget_id = ?")
                .use {
                    it.setUuid(1, budgetId)
                    it.executeUpdate()
                }
        }
    }

private fun Connection.zeroBalance(budgetId: UUID, tableName: String) {
    prepareStatement("update $tableName set balance = ? where budget_id = ?")
        .use {
            it.setBigDecimal(1, BigDecimal.ZERO.setScale(2))
            it.setUuid(2, budgetId)
            it.executeUpdate()
        }
}
