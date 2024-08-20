package bps.budget.jdbc

import bps.budget.BudgetConfigurations
import bps.budget.auth.User
import bps.budget.model.BudgetData
import io.kotest.core.spec.Spec
import io.kotest.mpp.atomics.AtomicReference
import java.util.UUID

interface BasicAccountsJdbcTestFixture : BaseJdbcTestFixture {
    override val configurations: BudgetConfigurations
        get() = BudgetConfigurations(sequenceOf("hasBasicAccountsJdbc.yml"))

    /**
     * Ensure that basic accounts are in place with zero balances in the DB before the test starts and deletes
     * transactions once the test is done.
     */
    fun Spec.createBasicAccountsBeforeSpec(budgetId: UUID, budgetName: String, user: User) {
        beforeSpec {
            jdbcDao.prepForFirstLoad()
//            try {
            deleteAccounts(budgetId, jdbcDao.connection)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//            try {
            jdbcDao.deleteBudget(budgetId)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//            try {
            jdbcDao.deleteUser(user.id)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//            try {
            jdbcDao.deleteUserByLogin(user.login)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
            upsertBasicAccounts(budgetName, user = user, budgetId = budgetId)
        }
    }

    fun Spec.resetAfterEach(budgetId: AtomicReference<UUID?>) {
        afterEach {
            cleanupTransactions(budgetId.value!!, jdbcDao.connection)
        }
    }

    fun Spec.resetBalancesAndTransactionAfterSpec(budgetId: UUID) {
        afterSpec {
            cleanupTransactions(budgetId, jdbcDao.connection)
        }
    }

    /**
     * This will be called automatically before a spec starts if you've called [createBasicAccountsBeforeSpec].
     * This ensures the DB contains the basic accounts with zero balances.
     */
    private fun upsertBasicAccounts(
        budgetName: String,
        generalAccountId: UUID = UUID.fromString("dfa8a21c-f0ad-434d-bcb5-9e37749fa81e"),
        user: User,
        budgetId: UUID,
    ) {
        jdbcDao.prepForFirstLoad()
        jdbcDao.save(
            BudgetData.withBasicAccounts(
                budgetName = budgetName,
                generalAccountId = generalAccountId,
                budgetId = budgetId,
            ),
            user,
        )
    }

}
