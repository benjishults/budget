package bps.budget.persistence.files

import bps.budget.model.BudgetData
import java.util.UUID

data class AccountsConfig(
    val generalAccountId: UUID,
    val category: List<CategoryAccountConfig>,
    val real: List<RealAccountConfig>,
    val draft: List<DraftAccountConfig>,
) {

    fun toBudgetData(): BudgetData {
        val categoryAccounts = category.map(CategoryAccountConfig::toCategoryAccount)
        val realAccounts = real.map(RealAccountConfig::toRealAccount)
        return BudgetData(
            generalAccount = categoryAccounts.find { it.id == generalAccountId }!!,
            categoryAccounts = categoryAccounts,
            realAccounts = realAccounts,
            draftAccounts = draft.map { draftAccountConfig: DraftAccountConfig ->
                draftAccountConfig.toDraftAccount(
                    realAccounts.find { it.id == draftAccountConfig.realCompanionId }!!,
                )
            },
        )
    }
}

fun BudgetData.toAccountsConfig(): AccountsConfig =
    AccountsConfig(
        generalAccount.id,
        categoryAccounts.map { it.toConfig() },
        realAccounts.map { it.toConfig() },
        draftAccounts.map { it.toConfig() },
    )
