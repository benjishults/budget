package bps.budget.file

import bps.budget.BudgetConfigurations
import bps.budget.auth.User
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.buildBudgetDao
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.loadBudgetData
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import java.util.UUID

class LoadingAccountsDataFileDaoTest : FreeSpec()/*,
    SimpleConsoleIoTestFixture*/ {

//    override val inputs: MutableList<String> = mutableListOf()
//    override val outputs: MutableList<String> = mutableListOf()

    init {

        "!budget with general food and wallet" {
//            inputs.addAll(listOf("test@test.com", ""))
            val configurations = BudgetConfigurations(sequenceOf("hasGeneralWalletAndFood.yml"))
//            val uiFunctions = ConsoleUiFacade(/*inputReader, outPrinter*/)
            buildBudgetDao(configurations.persistence)
                .use { budgetDao: BudgetDao ->
                    val budgetData = loadBudgetData(
                        user = User(
                            UUID.fromString("f0f209c8-1b1e-43b3-8799-2dba58524d02"),
                            configurations.user.defaultLogin!!,
                        ),
//                        uiFacade = uiFunctions,
                        budgetDao = budgetDao,
                        budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
                    )
                    budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
                    budgetData.realAccounts shouldHaveSize 1
                    budgetData.categoryAccounts shouldHaveSize 2
                }
        }

    }

}
