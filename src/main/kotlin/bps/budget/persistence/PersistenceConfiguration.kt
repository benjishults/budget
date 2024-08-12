package bps.budget.persistence

import bps.budget.persistence.files.FilesDao
import bps.budget.persistence.jdbc.JdbcDao

data class PersistenceConfiguration(
    val type: String,
    val file: FileConfig?,
    val jdbc: JdbcConfig?,
)

interface BudgetConfigLookup

data class FileConfig(
    val dataDirectory: String,
) : BudgetConfigLookup

data class JdbcConfig(
    val driver: String,
    val budgetName: String,
    val database: String = "budget",
    val schema: String = "public",
    val dbProvider: String = "postgresql",
    val port: String = "5432",
    val host: String = "localhost",
    val user: String? = null,
    val password: String? = null,
) : BudgetConfigLookup

fun interface ConfigFetcher : (PersistenceConfiguration) -> BudgetConfigLookup

fun budgetDaoBuilder(configurations: PersistenceConfiguration): BudgetDao =
    BudgetDataBuilderMap[configurations.type]!!
        .let { (configFetcher: ConfigFetcher, builder: (BudgetConfigLookup) -> BudgetDao) ->
            builder(configFetcher(configurations))
        }

val BudgetDataBuilderMap: Map<String, Pair<ConfigFetcher, (BudgetConfigLookup) -> BudgetDao>> =
    mapOf(
        "JDBC" to (ConfigFetcher { it.jdbc!! } to { JdbcDao(it as JdbcConfig) }),
        "FILE" to (ConfigFetcher { it.file!! } to { FilesDao(it as FileConfig) }),
    )
