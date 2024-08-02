@file:JvmName("DataCleanupUtils")

package bps.budget.jdbc

import bps.budget.persistence.jdbc.JdbcDao
import bps.jdbc.JdbcFixture

fun deleteTables(jdbcDao: JdbcDao, schema: String = "clean_after_test") {
    require(jdbcDao.config.schema == schema)
    with(JdbcFixture(jdbcDao.connection)) {
        transaction {
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
                }
        }
    }
}

fun cleanupBudget(jdbcDao: JdbcDao) {
    cleanupAccounts(jdbcDao)
    with(JdbcFixture(jdbcDao.connection)) {
        transaction {
            prepareStatement("delete from budgets where budget_name = ?")
                .use {
                    it.setString(1, jdbcDao.config.budgetName)
                    it.executeUpdate()
                }
        }
    }
}

fun cleanupAccounts(jdbcDao: JdbcDao) {
    cleanupTransactions(jdbcDao)
    with(JdbcFixture(jdbcDao.connection)) {
        transaction {
            prepareStatement("delete from draft_accounts where budget_name = ?")
                .use {
                    it.setString(1, jdbcDao.config.budgetName)
                    it.executeUpdate()
                }
            prepareStatement("delete from real_accounts where budget_name = ?")
                .use {
                    it.setString(1, jdbcDao.config.budgetName)
                    it.executeUpdate()
                }
            prepareStatement("delete from category_accounts where budget_name = ?")
                .use {
                    it.setString(1, jdbcDao.config.budgetName)
                    it.executeUpdate()
                }
        }
    }
}

fun cleanupTransactions(jdbcDao: JdbcDao) {
    with(JdbcFixture(jdbcDao.connection)) {
        transaction {
            prepareStatement("delete from transaction_items where budget_name = ?")
                .use {
                    it.setString(1, jdbcDao.config.budgetName)
                    it.execute()
                }
            prepareStatement("delete from transactions where budget_name = ?")
                .use {
                    it.setString(1, jdbcDao.config.budgetName)
                    it.execute()
                }
        }
    }
}
