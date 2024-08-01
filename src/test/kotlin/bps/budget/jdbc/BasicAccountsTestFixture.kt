package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.data.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.defaultCheckingAccountName
import bps.budget.model.defaultCheckingDraftsAccountName
import bps.budget.model.defaultFoodAccountName
import bps.budget.model.defaultNecessitiesAccountName
import bps.budget.model.defaultWalletAccountName
import bps.budget.persistence.jdbc.JdbcDao
import bps.jdbc.JdbcFixture
import io.kotest.core.spec.Spec
import java.sql.Connection
import java.util.UUID

interface BasicAccountsTestFixture : JdbcFixture {

    val configurations: BudgetConfigurations
    val jdbcDao: JdbcDao
    override val connection: Connection
        get() = jdbcDao.connection

    /**
     * Ensure that basic accounts are in place with zero balances in the DB before the test starts and deletes
     * transactions once the test is done.
     */
    fun Spec.useBasicAccounts() {
        beforeSpec {
            upsertBasicAccounts()
        }
        afterSpec {
            cleanupTransactions(jdbcDao)
            jdbcDao.close()
        }

    }

    /**
     * This will be called automatically before a spec starts if you've called [useBasicAccounts].
     * This ensures the DB contains the basic accounts with zero balances.
     */
    fun upsertBasicAccounts() {
        val generalAccount =
            CategoryAccount(
                "General",
                id = UUID.fromString("dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"),
            )
        val walletAccount = RealAccount(defaultWalletAccountName)
        val checkingAccount = RealAccount(defaultCheckingAccountName)
        val foodAccount = CategoryAccount(defaultFoodAccountName)
        val necessitiesAccount = CategoryAccount(defaultNecessitiesAccountName)
        val checkingDraftAccount = DraftAccount(defaultCheckingDraftsAccountName, realCompanion = checkingAccount)
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
