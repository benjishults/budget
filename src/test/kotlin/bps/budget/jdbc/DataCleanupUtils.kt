@file:JvmName("DataCleanupUtils")

package bps.budget.jdbc

import bps.jdbc.transactOrNull
import java.math.BigDecimal
import java.sql.Connection

fun dropTables(connection: Connection, schema: String) {
    require(schema == "clean_after_test")
    connection.transactOrNull {
        createStatement()
            .use { statement ->
                statement.execute("drop table if exists transaction_items")
                statement.execute("drop table if exists transactions")
                statement.execute("drop table if exists staged_draft_accounts")
                statement.execute("drop table if exists staged_real_accounts")
                statement.execute("drop table if exists staged_category_accounts")
                statement.execute("drop table if exists draft_accounts")
                statement.execute("drop table if exists real_accounts")
                statement.execute("drop table if exists category_accounts")
                statement.execute("drop table if exists budgets")
                statement.execute("drop table if exists timestamps")
            }
    }
}

fun deleteBudget(budgetName: String, connection: Connection) {
    deleteAccounts(budgetName, connection)
    connection.transactOrNull {
        prepareStatement("delete from budgets where budget_name = ?")
            .use {
                it.setString(1, budgetName)
                it.executeUpdate()
            }
    }
}

fun deleteAccounts(budgetName: String, connection: Connection) {
    cleanupTransactions(budgetName, connection)
    connection.transactOrNull {
        prepareStatement("delete from draft_accounts where budget_name = ?")
            .use {
                it.setString(1, budgetName)
                it.executeUpdate()
            }
        prepareStatement("delete from real_accounts where budget_name = ?")
            .use {
                it.setString(1, budgetName)
                it.executeUpdate()
            }
        prepareStatement("delete from category_accounts where budget_name = ?")
            .use {
                it.setString(1, budgetName)
                it.executeUpdate()
            }
    }
}

fun cleanupTransactions(budgetName: String, connection: Connection) {
    connection.transactOrNull {
        zeroBalance(budgetName, "category_accounts")
        zeroBalance(budgetName, "real_accounts")
        zeroBalance(budgetName, "draft_accounts")
        prepareStatement("delete from transaction_items where budget_name = ?")
            .use {
                it.setString(1, budgetName)
                it.executeUpdate()
            }
        prepareStatement("delete from transactions where budget_name = ?")
            .use {
                it.setString(1, budgetName)
                it.executeUpdate()
            }
    }
}

private fun Connection.zeroBalance(budgetName: String, tableName: String) {
    prepareStatement("update $tableName set balance = ? where budget_name = ?")
        .use {
            it.setBigDecimal(1, BigDecimal.ZERO.setScale(2))
            it.setString(2, budgetName)
            it.executeUpdate()
        }
}
