package bps.budget.persistence

import bps.budget.data.BudgetData
import bps.budget.persistence.files.loadAccountsFromFiles
import java.util.UUID

interface Config {
    val generalAccountId: UUID?
}

fun interface ConfigFetcher : (PersistenceConfiguration) -> Config
fun interface BudgetDataFactory : (Config) -> BudgetData

data class PersistenceConfiguration(
    val type: String,
    val file: FileConfig,
)

data class FileConfig(
    val dataDirectory: String,
    override val generalAccountId: UUID? = null,
) : Config

val BudgetDataBuilderMap: Map<String, Pair<ConfigFetcher, BudgetDataFactory>> =
    mapOf(
        "FILE" to
                (ConfigFetcher(PersistenceConfiguration::file) to
                        BudgetDataFactory { loadAccountsFromFiles(it as FileConfig) }),
    )
