package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.data.BudgetData
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFunctions
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual

class LoadingAccountsData : FreeSpec(), BasicAccountsTestFixture {

    override val configurations = BudgetConfigurations(sequenceOf("hasGeneralWalletAndFoodJdbc.yml"))
    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        useBasicAccounts()

        "budget with general food and wallet" {
            val uiFunctions = ConsoleUiFunctions()
            val budgetData = BudgetData(uiFunctions, jdbcDao)
            budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
            budgetData.realAccounts shouldHaveSize 2
            budgetData.categoryAccounts shouldHaveSize 3
            budgetData.draftAccounts shouldHaveSize 1
        }

    }

}
