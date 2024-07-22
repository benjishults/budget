package bps.budget

import bps.budget.data.BudgetData
import bps.budget.ui.ConsoleUiFunctions
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import java.util.UUID

class LoadingAccountsData : FreeSpec() {

    init {

        "budget with general food and wallet" {
            val uiFunctions = ConsoleUiFunctions()
            val configurations = BudgetConfigurations(sequenceOf("hasGeneralWalletAndFood.yml"))
            val budgetData = BudgetData(configurations.persistence, uiFunctions)
            budgetData.generalAccount.id shouldBeEqual UUID.fromString("dfa8a21c-f0ad-434d-bcb5-9e37749fa81e")
            budgetData.realAccounts shouldHaveSize 1
            budgetData.categoryAccounts shouldHaveSize 2
        }

    }

}
