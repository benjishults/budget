package bps.budget.persistence

interface BudgetConfigLookup {
//    val generalAccountId: UUID?
}

data class PersistenceConfiguration(
    val type: String,
    val file: FileConfig?,
    val jdbc: JdbcConfig?,
)

data class FileConfig(
    val dataDirectory: String,
) : BudgetConfigLookup

data class JdbcConfig(
    val driver: String,
    val url: String,
) : BudgetConfigLookup
