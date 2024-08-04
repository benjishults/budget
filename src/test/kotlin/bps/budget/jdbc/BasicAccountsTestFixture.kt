package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.model.BudgetData
import bps.budget.persistence.jdbc.JdbcDao
import bps.jdbc.JdbcFixture
import io.kotest.core.spec.Spec
import java.sql.Connection
import java.util.UUID

interface BasicJdbcTestFixture : JdbcFixture {

    val configurations: BudgetConfigurations
    val jdbcDao: JdbcDao
    override val connection: Connection
        get() = jdbcDao.connection

    fun Spec.closeJdbcAfterSpec() {
        afterSpec {
            jdbcDao.close()
        }
    }

}

interface NoDataJdbcTestFixture : BasicJdbcTestFixture {
    override val configurations: BudgetConfigurations
        get() = BudgetConfigurations(sequenceOf("noDataJdbc.yml"))

    fun Spec.dropAllAfterEach() {
        afterEach {
            dropTables(jdbcDao.connection, jdbcDao.config.schema)
        }
    }

}

interface BasicAccountsTestFixture : BasicJdbcTestFixture {
    override val configurations: BudgetConfigurations
        get() = BudgetConfigurations(sequenceOf("hasBasicAccountsJdbc.yml"))

    /**
     * Ensure that basic accounts are in place with zero balances in the DB before the test starts and deletes
     * transactions once the test is done.
     */
    fun Spec.useBasicAccounts() {
        closeJdbcAfterSpec()
        beforeSpec {
            deleteAccounts(jdbcDao.config.budgetName, jdbcDao.connection)
            upsertBasicAccounts()
        }
    }

    fun Spec.resetAfterEach() {
        afterEach {
            cleanupTransactions(jdbcDao.config.budgetName, jdbcDao.connection)
        }
    }

    fun Spec.resetBalancesAndTransactionAfterSpec() {
        afterSpec {
            cleanupTransactions(jdbcDao.config.budgetName, jdbcDao.connection)
        }
    }

    /**
     * This will be called automatically before a spec starts if you've called [useBasicAccounts].
     * This ensures the DB contains the basic accounts with zero balances.
     */
    fun upsertBasicAccounts(generalAccountId: UUID = UUID.fromString("dfa8a21c-f0ad-434d-bcb5-9e37749fa81e")) {
        jdbcDao.save(BudgetData.withBasicAccounts(generalAccountId = generalAccountId))
    }

}
