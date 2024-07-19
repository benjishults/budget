package bps.budget.persistence

import bps.budget.data.BudgetData
import java.util.UUID

data class AccountsConfig(
    val generalAccountId: UUID,
    val category: List<CategoryAccountConfig>,
    val real: List<RealAccountConfig>,
    val draft: List<DraftAccountConfig>,
) {
    fun toBudgetData(): BudgetData {
        val generalAccount = category.find { it.id == generalAccountId }!!
        return BudgetData(
            generalAccount = generalAccount,
            virtualAccounts = listOf(generalAccount),
            realAccounts = real,
            draftAccounts = draft,
        )
    }
}

fun BudgetData.toAccountsConfig(): AccountsConfig =
    AccountsConfig(generalAccount.id, virtualAccounts, realAccounts, draftAccounts)
