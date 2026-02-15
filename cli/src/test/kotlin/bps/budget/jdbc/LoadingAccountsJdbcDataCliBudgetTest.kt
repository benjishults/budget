package bps.budget.jdbc

import bps.budget.JdbcCliBudgetDao
import bps.budget.JdbcInitializingBudgetDao
import bps.budget.jdbc.test.BasicAccountsJdbcCliBudgetTestFixture
import bps.jdbc.HikariYamlConfig
import bps.jdbc.JdbcConfig
import bps.jdbc.getConfigFromResource
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LoadingAccountsJdbcDataCliBudgetTest : FreeSpec() {

    val jdbcConfig: JdbcConfig
    val hikariConfig: HikariYamlConfig

    init {
        getConfigFromResource("hasBasicAccountsJdbc.yml")
            .also {
                jdbcConfig = it.first
                hikariConfig = it.second
            }
    }

    val budgetName: String = "${this::class.simpleName!!.substring(0, 22)}-${Uuid.random()}"
    val userName: String = "$budgetName@example.com"
    val basicAccountsJdbcCliBudgetTestFixture: BasicAccountsJdbcCliBudgetTestFixture =
        BasicAccountsJdbcCliBudgetTestFixture(
            jdbcConfig,
            budgetName,
            userName,
        )

    init {
//        val budgetConfigurations = BudgetConfigurations(sequenceOf("hasBasicAccountsJdbc.yml"))
//        val basicAccountsJdbcCliBudgetTestFixture = BasicAccountsJdbcCliBudgetTestFixture(
//            budgetConfigurations.persistence.jdbc!!,
//            budgetConfigurations.budget.name,
//            budgetConfigurations.user.defaultLogin,
//        )
//        val userId = Uuid.random()
        with(basicAccountsJdbcCliBudgetTestFixture) {
            val initializingBudgetDao = JdbcInitializingBudgetDao(budgetName, dataSource)
            val cliBudgetDao = JdbcCliBudgetDao(userName, dataSource)
            createBasicAccountsBeforeSpec(
                budgetName,
                userName,
                TimeZone.of("America/Chicago"),
                Clock.System,
            ) {
                initializingBudgetDao.ensureTablesAndIndexes()
            }
            cleanUpEverythingAfterSpec(userName)

            "budget with basic accounts" {
                val budgetData = cliBudgetDao.load(budgetName, userName, accountDao)
//                budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
                budgetData.realAccounts shouldHaveSize 2
                budgetData.categoryAccounts shouldHaveSize 14
                budgetData.draftAccounts shouldHaveSize 1
            }
        }

    }

}
