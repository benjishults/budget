package bps.budget.data

import bps.budget.model.Account
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.persistence.BudgetConfigLookup
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.FileConfig
import bps.budget.persistence.JdbcConfig
import bps.budget.persistence.PersistenceConfiguration
import bps.budget.persistence.files.BudgetFilesDao
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.transaction.Transaction
import bps.budget.ui.UiFunctions
import java.util.UUID

class BudgetData(
    val generalAccount: CategoryAccount,
    categoryAccounts: List<CategoryAccount>,
    realAccounts: List<RealAccount> = emptyList(),
    draftAccounts: List<DraftAccount> = emptyList(),
    transactions: List<Transaction> = emptyList(),
) {

    private val _categoryAccounts: MutableList<CategoryAccount> = categoryAccounts.toMutableList()
    val categoryAccounts: List<CategoryAccount> = _categoryAccounts
    private val _realAccounts: MutableList<RealAccount> = realAccounts.toMutableList()
    val realAccounts: List<RealAccount> = _realAccounts
    private val _draftAccounts: MutableList<DraftAccount> = draftAccounts.toMutableList()
    val draftAccounts: List<DraftAccount> = _draftAccounts
    private val _transactions: MutableList<Transaction> = transactions.toMutableList()
    val transactions: List<Transaction> = _transactions
    private val byId: MutableMap<UUID, Account> = mutableMapOf()

    fun addRealAccount(account: RealAccount) {
        _realAccounts.add(account)
    }

    fun addCategoryAccount(account: CategoryAccount) {
        _categoryAccounts.add(account)
    }

    companion object {

        /**
         * Will build basic data if there is an error getting it from a file.
         */
        operator fun invoke(persistenceConfiguration: PersistenceConfiguration, uiFunctions: UiFunctions): BudgetData =
            try {
                BudgetDataBuilderMap[persistenceConfiguration.type]
                    ?.let { (configFetcher: ConfigFetcher, builder: (BudgetConfigLookup) -> BudgetDao<*>) ->
                        builder(configFetcher(persistenceConfiguration))
                            .load()
                    }
            } catch (loadException: DataConfigurationException) {
                uiFunctions.createGeneralAccount()
                    .let {
                        BudgetData(it, listOf(it), emptyList())
                    }
            }
                ?: throw Exception("Unsupported persistence type: ${persistenceConfiguration.type}")
    }

}

fun interface ConfigFetcher : (PersistenceConfiguration) -> BudgetConfigLookup

val BudgetDataBuilderMap: Map<String, Pair<ConfigFetcher, (BudgetConfigLookup) -> BudgetDao<*>>> =
    mapOf(
        "JDBC" to (ConfigFetcher { it.jdbc!! } to { JdbcDao(it as JdbcConfig) }),
        "FILE" to
                (ConfigFetcher { it.file!! } to
                        { BudgetFilesDao(it as FileConfig) }),
    )
