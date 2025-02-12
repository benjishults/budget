package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import io.kotest.core.spec.Spec

interface NoDataJdbcTestFixture : BaseJdbcTestFixture {
    override val configurations: BudgetConfigurations
        get() = BudgetConfigurations(sequenceOf("noDataJdbc.yml"))

    fun Spec.dropAllBeforeEach() {
        beforeEach {
            dropTables(jdbcDao.connection, jdbcDao.config.schema)
        }
    }

}
