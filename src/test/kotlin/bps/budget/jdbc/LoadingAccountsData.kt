package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.data.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFunctions
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import java.util.UUID

class LoadingAccountsData : FreeSpec() {

    init {
        val configurations = BudgetConfigurations(sequenceOf("hasGeneralWalletAndFoodJdbc.yml"))
        val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

        beforeSpec {
            upsertBasicAccounts(jdbcDao)
        }

        "budget with general food and wallet" {
            val uiFunctions = ConsoleUiFunctions()
            val budgetData = BudgetData(configurations.persistence, uiFunctions, jdbcDao)
            budgetData.generalAccount.id.toString() shouldBeEqual "dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"
            budgetData.realAccounts shouldHaveSize 2
            budgetData.categoryAccounts shouldHaveSize 3
            budgetData.draftAccounts shouldHaveSize 1
        }

        afterSpec {
            cleanupTransactions(jdbcDao)
            jdbcDao.close()
        }
    }

    private fun upsertBasicAccounts(jdbcDao: JdbcDao) {
        val generalAccount =
            CategoryAccount(
                "General",
                id = UUID.fromString("dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"),
            )
        val walletAccount = RealAccount("Wallet")
        val checkingAccount = RealAccount("Checking")
        val foodAccount = CategoryAccount("Food")
        val necessitiesAccount = CategoryAccount("Necessities")
        val checkingDraftAccount = DraftAccount("CheckingDraft", realCompanion = checkingAccount)
        val budgetData =
            BudgetData(
                generalAccount,
                listOf(generalAccount, foodAccount, necessitiesAccount),
                listOf(walletAccount, checkingAccount),
                listOf(checkingDraftAccount),
            )
        jdbcDao.save(budgetData)
    }

}
