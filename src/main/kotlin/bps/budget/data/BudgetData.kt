package bps.budget.data

import bps.budget.persistence.AccountConfig
import bps.budget.persistence.BudgetDataBuilderMap
import bps.budget.persistence.BudgetDataFactory
import bps.budget.persistence.CategoryAccountConfig
import bps.budget.persistence.ConfigFetcher
import bps.budget.persistence.DraftAccountConfig
import bps.budget.persistence.PersistenceConfiguration
import bps.budget.persistence.RealAccountConfig
import bps.budget.ui.UiFunctions
import com.uchuhimo.konf.source.LoadException
import java.util.UUID

class BudgetData(
    val generalAccount: CategoryAccountConfig,
    virtualAccounts: List<CategoryAccountConfig>,
    realAccounts: List<RealAccountConfig> = emptyList(),
    draftAccounts: List<DraftAccountConfig> = emptyList(),
) {

    private val _virtualAccounts: MutableList<CategoryAccountConfig> = virtualAccounts.toMutableList()
    val virtualAccounts: List<CategoryAccountConfig> = _virtualAccounts
    private val _realAccounts: MutableList<RealAccountConfig> = realAccounts.toMutableList()
    val realAccounts: List<RealAccountConfig> = _realAccounts
    private val _draftAccounts: MutableList<DraftAccountConfig> = draftAccounts.toMutableList()
    val draftAccounts: List<DraftAccountConfig> = _draftAccounts
    private val byId: MutableMap<UUID, AccountConfig> = mutableMapOf()

    fun addRealAccount(account: RealAccountConfig) {
        _realAccounts.add(account)
    }

    fun addVirtualAccount(account: CategoryAccountConfig) {
        _virtualAccounts.add(account)
    }

    companion object {
        /**
         * Will build basic data if there is an error getting it from a file.
         */
        operator fun invoke(persistenceConfiguration: PersistenceConfiguration, uiFunctions: UiFunctions): BudgetData =
            try {
                BudgetDataBuilderMap[persistenceConfiguration.type]
                    ?.let { (configFetcher: ConfigFetcher, builder: BudgetDataFactory) ->
                        builder(configFetcher(persistenceConfiguration))
                    }
            } catch (loadException: LoadException) {
                uiFunctions.createGeneralAccount()
                    .let {
                        BudgetData(it, listOf(it), emptyList())
                    }
            }
                ?: throw Exception("Unsupported persistence type: ${persistenceConfiguration.type}")
    }

}


