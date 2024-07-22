package bps.budget.ui

import bps.budget.data.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.persistence.PersistenceConfiguration
import bps.budget.persistence.toAccountsConfig
import bps.config.ApiObjectMapperConfigurer
import bps.config.convertToPath
import bps.console.inputs.RecursivePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.FileWriter
import kotlin.io.path.Path

interface UiFunctions {
    fun createGeneralAccount(): CategoryAccount
    fun saveData(
        persistenceConfiguration: PersistenceConfiguration,
        budgetData: BudgetData,
        accountsFileName: String = "accounts.yml",
    )
}

class ConsoleUiFunctions(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFunctions {

    override fun createGeneralAccount(): CategoryAccount =
        RecursivePrompt(
            listOf(
                SimplePromptWithDefault<String>(
                    """
            |Looks like this is your first time running Budget.
            |Enter the name for your "General" account""".trimMargin(),
                    "General",
                    inputReader,
                    outPrinter,
                ),
                SimplePromptWithDefault(
                    """Enter the description for your "General" account""".trimMargin(),
                    "Income is automatically deposited here and allowances are made from here.",
                    inputReader,
                    outPrinter,
                ),
            ),
        ) { CategoryAccount(it[0] as String, it[1] as String) }
            .getResult()

    override fun saveData(
        persistenceConfiguration: PersistenceConfiguration,
        budgetData: BudgetData,
        accountsFileName: String,
    ) {
        // TODO for now, let's get the accounts file saved
        Path(convertToPath(persistenceConfiguration.file.dataDirectory)).toFile().mkdirs()
        val accountsPath = Path(convertToPath(persistenceConfiguration.file.dataDirectory), accountsFileName)
        val objectMapper = ObjectMapper(YAMLFactory()).also { ApiObjectMapperConfigurer.configureObjectMapper(it) }
        objectMapper.writeValue(
            FileWriter(
                accountsPath.toFile()
                    .apply {
                        createNewFile()
                    },
            ),
            budgetData.toAccountsConfig(),
        )
    }
}
