package bps.budget.persistence.files

import bps.budget.data.BudgetData
import bps.budget.persistence.AccountsConfig
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.FileConfig
import bps.budget.persistence.toAccountsConfig
import bps.config.ApiObjectMapperConfigurer
import bps.config.ConfigurationHelper
import bps.config.convertToPath
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.github.nhubbard.konf.source.LoadException
import io.github.nhubbard.konf.toValue
import java.io.FileWriter
import kotlin.io.path.Path

/**
 * Loads data from files to produce an instance of [BudgetData]
 */
class FilesDao(
    override val config: FileConfig,
    val accountsFileName: String = "accounts.yml",
) : BudgetDao<FileConfig> {
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

    override fun close() {
    }

}
