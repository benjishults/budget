package bps.budget.jdbc

import bps.budget.persistence.jdbc.JdbcDao

fun deleteTables(jdbcDao: JdbcDao) {
    require(jdbcDao.config.schema == "clean_after_test")

    jdbcDao.connection.createStatement()
        .use { statement ->
            statement.execute("drop table if exists transaction_items")
            statement.execute("drop table if exists transactions")
            statement.execute("drop table if exists draft_accounts")
            statement.execute("drop table if exists real_accounts")
            statement.execute("drop table if exists category_accounts")
            statement.execute("drop table if exists budgets")
        }
    jdbcDao.connection.commit()
}

fun cleanupBudget(jdbcDao: JdbcDao) {
    cleanupAccounts(jdbcDao)
    jdbcDao.connection.prepareStatement("delete from budgets where budget_name = ?")
        .use {
            it.setString(1, jdbcDao.config.budgetName)
            it.executeUpdate()
        }
    jdbcDao.connection.commit()
}

fun cleanupAccounts(jdbcDao: JdbcDao) {
    cleanupTransactions(jdbcDao)
    jdbcDao.connection.prepareStatement("delete from draft_accounts where budget_name = ?")
        .use {
            it.setString(1, jdbcDao.config.budgetName)
            it.execute()
        }
    jdbcDao.connection.prepareStatement("delete from real_accounts where budget_name = ?")
        .use {
            it.setString(1, jdbcDao.config.budgetName)
            it.execute()
        }
    jdbcDao.connection.prepareStatement("delete from category_accounts where budget_name = ?")
        .use {
            it.setString(1, jdbcDao.config.budgetName)
            it.execute()
        }
    jdbcDao.connection.commit()
}

fun cleanupTransactions(jdbcDao: JdbcDao) {
    jdbcDao.connection.prepareStatement("delete from transaction_items where budget_name = ?")
        .use {
            it.setString(1, jdbcDao.config.budgetName)
            it.execute()
        }
    jdbcDao.connection.prepareStatement("delete from transactions where budget_name = ?")
        .use {
            it.setString(1, jdbcDao.config.budgetName)
            it.execute()
        }
    jdbcDao.connection.commit()
}
