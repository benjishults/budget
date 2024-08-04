package bps.budget.file

import bps.budget.BudgetConfigurations
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.budgetDaoBuilder
import bps.budget.persistence.budgetDataFactory
import bps.budget.ui.ConsoleUiFacade
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual

class LoadingAccountsData : FreeSpec() {

    init {

        "budget with general food and wallet" {
            val configurations = BudgetConfigurations(sequenceOf("hasGeneralWalletAndFood.yml"))
            val uiFunctions = ConsoleUiFacade()
            budgetDaoBuilder(configurations.persistence).use { budgetDao: BudgetDao<*> ->
                val budgetData = budgetDataFactory(uiFunctions, budgetDao)
                budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
                budgetData.realAccounts shouldHaveSize 1
                budgetData.categoryAccounts shouldHaveSize 2
            }
        }

    }

}
