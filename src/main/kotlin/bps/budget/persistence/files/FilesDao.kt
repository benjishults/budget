package bps.budget.persistence.files

import bps.budget.auth.User
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.FileConfig
import bps.config.ApiObjectMapperConfigurer
import bps.config.convertToPath
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.FileWriter
import kotlin.io.path.Path

/**
 * Loads data from files to produce an instance of [BudgetData].
 */
@Deprecated("this has not been kept up-to-date and doesn't work", ReplaceWith("JdbcDao"))
class FilesDao(
    val config: FileConfig,
    val accountsFileName: String = "accounts.yml",
) : BudgetDao {
//    /**
//     * @throws DataConfigurationException if the files aren't set up properly
//     */
//    override fun load(budgetId: UUID): BudgetData =
//        try {
//            ConfigurationHelper(sequenceOf("${convertToPath(config.dataDirectory)}/accounts.yml"))
//                .config
//                .at("accounts")
//                .toValue<AccountsConfig>()
//                .let { accountsConfig: AccountsConfig ->
//                    accountsConfig.toBudgetData()
//                }
//        } catch (loadException: LoadException) {
//            throw DataConfigurationException(loadException)
//        }

    override fun save(data: BudgetData, user: User) {
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

    override fun fetchTransactions(account: Account, data: BudgetData, limit: Int, offset: Int): List<Transaction> {
        TODO("Not yet implemented")
    }

    override fun close() {
    }

//    private fun AccountsConfig.toBudgetData(): BudgetData {
//        val categoryAccounts = category.map(CategoryAccountConfig::toCategoryAccount)
//        val realAccounts = real.map(RealAccountConfig::toRealAccount)
//        return BudgetData(
//            // broken but not using file config anymore, so don't care
//            id = UUID.randomUUID(),
//            name =
//            timeZone = TimeZone.currentSystemDefault(),  // NOTE fix this
//            generalAccount = categoryAccounts.find { it.id == generalAccountId }!!,
//            categoryAccounts = categoryAccounts,
//            realAccounts = realAccounts,
//            draftAccounts = draft.map { draftAccountConfig: DraftAccountConfig ->
//                draftAccountConfig.toDraftAccount(
//                    realAccounts.find { it.id == draftAccountConfig.realCompanionId }!!,
//                )
//            },
//        )
//    }

}
