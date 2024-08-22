package bps.budget.persistence

import bps.budget.persistence.files.FilesDao
import bps.budget.persistence.jdbc.JdbcDao
import org.apache.commons.validator.routines.EmailValidator

data class PersistenceConfiguration(
    val type: String,
    val file: FileConfig? = null,
    val jdbc: JdbcConfig? = null,
)

data class UserConfiguration(
    val defaultLogin: String? = null,
    val defaultTimeZone: String? = null,
    val numberOfItemsInScrollingList: Int = 30,
//    val defaultBudgetName: String?,
) {
    init {
        require(
            defaultLogin === null ||
                    EmailValidator.getInstance().isValid(defaultLogin),
        ) { "defaultLogin, if provided, must be a valid email address.  Was '$defaultLogin'" }
    }
}

interface BudgetConfig {
    val budgetName: String?
}

data class FileConfig(
    val dataDirectory: String,
    override val budgetName: String? = null,
) : BudgetConfig

data class JdbcConfig(
    val driver: String,
    override val budgetName: String? = null,
    val database: String = "budget",
    val schema: String = "public",
    val dbProvider: String = "postgresql",
    val port: String = "5432",
    val host: String = "localhost",
    val user: String? = null,
    val password: String? = null,
) : BudgetConfig

fun interface ConfigSelector : (PersistenceConfiguration) -> BudgetConfig
fun interface DaoBuilder : (BudgetConfig) -> BudgetDao

fun buildBudgetDao(configurations: PersistenceConfiguration): BudgetDao =
    budgetDataBuilderMap[configurations.type]!!
        .let { (configSelector: ConfigSelector, daoBuilder: DaoBuilder) ->
            daoBuilder(configSelector(configurations))
        }

val budgetDataBuilderMap: Map<String, Pair<ConfigSelector, DaoBuilder>> =
    mapOf(
        "JDBC" to (ConfigSelector { it.jdbc!! } to DaoBuilder { JdbcDao(it as JdbcConfig) }),
        "FILE" to (ConfigSelector { it.file!! } to DaoBuilder { FilesDao(it as FileConfig) }),
    )

fun getBudgetNameFromPersistenceConfig(configuration: PersistenceConfiguration) =
    budgetDataBuilderMap[configuration.type]!!
        .first(configuration)
        .budgetName

