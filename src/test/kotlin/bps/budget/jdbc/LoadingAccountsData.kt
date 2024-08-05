package bps.budget.jdbc

import bps.budget.persistence.budgetDataFactory
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual

class LoadingAccountsData : FreeSpec(), BasicAccountsTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        createBasicAccountsBeforeSpec()
        closeJdbcAfterSpec()

        "budget with basic accounts" {
            val uiFunctions = ConsoleUiFacade()
            val budgetData = budgetDataFactory(uiFunctions, jdbcDao)
            budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
            budgetData.realAccounts shouldHaveSize 2
            budgetData.categoryAccounts shouldHaveSize 10
            budgetData.draftAccounts shouldHaveSize 1
        }

    }

}
