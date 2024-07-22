package bps.budget.persistence

import bps.budget.data.BudgetData
import bps.budget.persistence.files.loadAccountsFromFiles

interface BudgetConfigLookup {
//    val generalAccountId: UUID?
}

data class PersistenceConfiguration(
    val type: String,
    val file: FileConfig,
)

data class FileConfig(
    val dataDirectory: String,
) : BudgetConfigLookup
