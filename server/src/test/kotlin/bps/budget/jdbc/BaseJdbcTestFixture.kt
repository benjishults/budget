package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.persistence.jdbc.JdbcDao
import bps.jdbc.JdbcFixture
import io.kotest.core.spec.Spec
import java.sql.Connection

interface BaseJdbcTestFixture : JdbcFixture {

    val configurations: BudgetConfigurations
    val jdbcDao: JdbcDao
    val connection: Connection
        get() = jdbcDao.connection

    fun Spec.closeJdbcAfterSpec() {
        afterSpec {
            jdbcDao.close()
        }
    }

}
