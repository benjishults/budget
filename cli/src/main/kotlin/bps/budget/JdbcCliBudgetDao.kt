package bps.budget

import bps.budget.model.AccountType
import bps.budget.model.AccountsHolder
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.toCategoryAccount
import bps.budget.model.toChargeAccount
import bps.budget.model.toDraftAccount
import bps.budget.model.toRealAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.DataConfigurationException
import bps.jdbc.JdbcFixture
import bps.jdbc.JdbcFixture.Companion.transactOrThrow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO this class is doing too much... could be split up... See how it's done in server.
@OptIn(ExperimentalUuidApi::class)
class JdbcCliBudgetDao(
    val budgetName: String,
    private val dataSource: DataSource,
) : CliBudgetDao, JdbcFixture {

    private data class BudgetDataInfo(
        val generalAccountId: Uuid,
        val timeZone: TimeZone,
        val analyticsStart: Instant,
        val budgetName: String,
        val budgetId: Uuid,
        val userName: String,
        val userId: Uuid,
    )

    /**
     * Just loads top-level account info.  Details of transactions are loaded on-demand.
     * @throws DataConfigurationException if data isn't found.
     */
    override fun load(budgetName: String, userName: String, accountDao: AccountDao): BudgetData {
//        val (
//            generalAccountId: Uuid,
//            timeZone: TimeZone,
//            analyticsStart: Instant,
//            budgetName: String,
//        ) =
        dataSource.transactOrThrow {
            try {
                prepareStatement(
                    """
                            select b.general_account_name, b.id as budget_id, u.id as user_id, ba.time_zone, ba.budget_name, ba.analytics_start, a.id as general_account_id
                            from budgets b
                                join budget_access ba on b.id = ba.budget_id
                                join users u on u.id = ba.user_id
                                join accounts a
                                    on a.name = b.general_account_name
                                    and a.type = 'category'
                                    and a.budget_id = b.id
                            where ba.budget_name = ?
                                and u.login = ?
                        """.trimIndent(),
                )
                    .use { getBudget: PreparedStatement ->
                        getBudget.setString(1, budgetName)
                        getBudget.setString(2, userName)
                        getBudget.executeQuery()
                            .use { result: ResultSet ->
                                if (result.next()) {
                                    BudgetDataInfo(
                                        generalAccountId = result.getUuid("general_account_id")!!,
                                        timeZone = result.getString("time_zone")
                                            ?.let { timeZone -> TimeZone.of(timeZone) }
                                            ?: TimeZone.currentSystemDefault(),
                                        analyticsStart = result.getInstant("analytics_start"),
                                        budgetName = budgetName,
                                        budgetId = result.getUuid("budget_id")!!,
                                        userName = userName,
                                        userId = result.getUuid("user_id")!!,
                                    )
                                } else
                                    throw DataConfigurationException("Budget data not found for name: $budgetName")
                            }
                    }
            } catch (ex: Exception) {
                if (ex is DataConfigurationException) {
                    throw ex
                } else
                    throw DataConfigurationException(ex)
            }
        }
            .let { budgetDataInfo ->

                // TODO pull out duplicate code in these next three sections
                val categoryAccountsHolder =
                    AccountsHolder(
                        active = accountDao
                            .getActiveAccounts(
                                type = AccountType.category.name,
                                budgetId = budgetDataInfo.budgetId,
                            )
                            .map { it.toCategoryAccount()!! },
                        inactive = accountDao
                            .getDeactivatedAccounts(
                                type = AccountType.category.name,
                                budgetId = budgetDataInfo.budgetId,
                            )
                            .map { it.toCategoryAccount()!! },
                    )
                val generalAccount: CategoryAccount =
                    categoryAccountsHolder
                        .active
                        .find { it.id == budgetDataInfo.generalAccountId }!!
                val realAccountsHolder =
                    AccountsHolder(
                        active = accountDao
                            .getActiveAccounts(
                                type = AccountType.real.name,
                                budgetId = budgetDataInfo.budgetId,
                            )
                            .map { it.toRealAccount()!! },
                        inactive = accountDao
                            .getDeactivatedAccounts(AccountType.real.name, budgetDataInfo.budgetId)
                            .map { it.toRealAccount()!! },
                    )
                val chargeAccountsHolder =
                    AccountsHolder(
                        active = accountDao
                            .getActiveAccounts(
                                type = AccountType.charge.name,
                                budgetId = budgetDataInfo.budgetId,
                            )
                            .map { it.toChargeAccount()!! },
                        inactive =
                            accountDao
                                .getDeactivatedAccounts(AccountType.charge.name, budgetDataInfo.budgetId)
                                .map { it.toChargeAccount()!! },
                    )
                val draftAccountsHolder = AccountsHolder(
                    active = accountDao.getActiveAccounts(
                        AccountType.draft.name,
                        budgetDataInfo.budgetId,
                    )
                        .map {
                            it.toDraftAccount { realId: Uuid ->
                                realAccountsHolder
                                    .allAccounts
                                    .firstOrNull { it.id == realId }
                            }!!
                        },
                    inactive = accountDao.getDeactivatedAccounts(
                        AccountType.draft.name,
                        budgetDataInfo.budgetId,
                    )
                        .map {
                            it.toDraftAccount { realId: Uuid ->
                                realAccountsHolder
                                    .allAccounts
                                    .firstOrNull { it.id == realId }
                            }!!
                        },
                )
                return BudgetData(
                    budgetDataInfo.budgetId,
                    budgetName,
                    budgetDataInfo.timeZone,
                    budgetDataInfo.analyticsStart,
                    generalAccount,
                    categoryAccountsHolder,
                    realAccountsHolder,
                    chargeAccountsHolder,
                    draftAccountsHolder,
                )
            }
    }

    override fun close() {
        super.close()
    }

}
