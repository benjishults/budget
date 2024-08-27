package bps.budget.persistence.files

import bps.budget.model.BudgetData
import java.util.UUID

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
data class AccountsConfig(
    val generalAccountId: UUID,
    val category: List<CategoryAccountConfig>,
    val real: List<RealAccountConfig>,
    val draft: List<DraftAccountConfig>,
) {

}

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
fun BudgetData.toAccountsConfig(): AccountsConfig =
    AccountsConfig(
        generalAccount.id,
        categoryAccounts.map { it.toConfig() },
        realAccounts.map { it.toConfig() },
        draftAccounts.map { it.toConfig() },
    )
