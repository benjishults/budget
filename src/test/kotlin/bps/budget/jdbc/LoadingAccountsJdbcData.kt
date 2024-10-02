package bps.budget.jdbc

import bps.budget.auth.User
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.jdbc.JdbcDao
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import java.util.UUID

class LoadingAccountsJdbcData : FreeSpec(), BasicAccountsJdbcTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        val budgetId = UUID.fromString("89bc165a-ee70-43a4-b637-2774bcfc3ea4")
        val userId = UUID.fromString("f0f209c8-1b1e-43b3-8799-2dba58524d02")
        createBasicAccountsBeforeSpec(
            budgetId,
            getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
            User(userId, configurations.user.defaultLogin!!),
        )
        closeJdbcAfterSpec()

        "budget with basic accounts" {
//            val uiFunctions = ConsoleUiFacade()
            val budgetData = jdbcDao.load(budgetId, userId)
            budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
            budgetData.realAccounts shouldHaveSize 2
            budgetData.categoryAccounts shouldHaveSize 14
            budgetData.draftAccounts shouldHaveSize 1
        }

    }

}
