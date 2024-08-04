package bps.budget.model

import bps.budget.BudgetConfigurations
import bps.budget.persistence.budgetDaoBuilder
import bps.budget.ui.ConsoleUiFacade
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual

class AccountTest : FreeSpec() {

    init {
        "load a few accounts" - {
            val configurations = BudgetConfigurations(sequenceOf("hasGeneralWalletAndFood.yml"))
            val uiFunctions = ConsoleUiFacade()
            budgetDaoBuilder(configurations.persistence).use { budgetDao ->

                val budgetData = BudgetData(uiFunctions, budgetDao)
                budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
                budgetData.realAccounts shouldHaveSize 1
                budgetData.categoryAccounts shouldHaveSize 2

                val food: CategoryAccount = budgetData.categoryAccounts.find { it.name == "Food" }
                    ?: fail("There should be a Food account in there")
                val wallet: RealAccount =
                    budgetData.realAccounts.firstOrNull() ?: fail("There should be a real account in there")

                "record income" {

                }
            }

        }
    }

}
