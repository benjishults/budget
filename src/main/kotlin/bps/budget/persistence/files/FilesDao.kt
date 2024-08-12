package bps.budget.persistence.files

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.FileConfig
import bps.config.ApiObjectMapperConfigurer
import bps.config.ConfigurationHelper
import bps.config.convertToPath
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.github.nhubbard.konf.source.LoadException
import io.github.nhubbard.konf.toValue
import java.io.FileWriter
import java.util.TimeZone
import kotlin.io.path.Path

/**
 * Loads data from files to produce an instance of [BudgetData]
 */
class FilesDao(
    val config: FileConfig,
    val accountsFileName: String = "accounts.yml",
) : BudgetDao {
    /**
     * @throws DataConfigurationException if the files aren't set up properly
     */
    override fun load(): BudgetData =
        try {
            ConfigurationHelper(sequenceOf("${convertToPath(config.dataDirectory)}/accounts.yml"))
                .config
                .at("accounts")
                .toValue<AccountsConfig>()
                .let { accountsConfig: AccountsConfig ->
                    accountsConfig.toBudgetData()
                }
        } catch (loadException: LoadException) {
            throw DataConfigurationException(loadException)
        }

    override fun save(data: BudgetData) {
        // TODO for now, let's get the accounts file saved
        Path(convertToPath(config.dataDirectory)).toFile().mkdirs()
        val accountsPath = Path(convertToPath(config.dataDirectory), accountsFileName)
        val objectMapper = ObjectMapper(YAMLFactory()).also { ApiObjectMapperConfigurer.configureObjectMapper(it) }
        objectMapper.writeValue(
            FileWriter(
                accountsPath.toFile()
                    .apply {
                        createNewFile()
                    },
            ),
            data.toAccountsConfig(),
        )
    }

    override fun fetchTransactions(account: Account, data: BudgetData, limit: Int, offset: Long): List<Transaction> {
        TODO("Not yet implemented")
    }

    override fun close() {
    }

    private fun AccountsConfig.toBudgetData(): BudgetData {
        val categoryAccounts = category.map(CategoryAccountConfig::toCategoryAccount)
        val realAccounts = real.map(RealAccountConfig::toRealAccount)
        return BudgetData(
            timeZone = TimeZone.getDefault(),  // NOTE fix this
            generalAccount = categoryAccounts.find { it.id == generalAccountId }!!,
            categoryAccounts = categoryAccounts,
            realAccounts = realAccounts,
            draftAccounts = draft.map { draftAccountConfig: DraftAccountConfig ->
                draftAccountConfig.toDraftAccount(
                    realAccounts.find { it.id == draftAccountConfig.realCompanionId }!!,
                )
            },
        )
    }

}
