package bps.budget.persistence

import bps.budget.data.BudgetData
import bps.budget.model.RealAccount
import java.util.UUID

data class AccountsConfig(
    val generalAccountId: UUID,
    val category: List<CategoryAccountConfig>,
    val real: List<RealAccountConfig>,
    val draft: List<DraftAccountConfig>,
) {
    fun toBudgetData(): BudgetData {
        val generalAccount = category.find { it.id == generalAccountId }!!.toCategoryAccount()
        val realAccounts = real.map { realAccountConfig: RealAccountConfig ->
            realAccountConfig
                .toRealAccount()
                .also { currentRealAccount: RealAccount ->
                    realAccountConfig.draftCompanionId
                        ?.let { companionId: UUID ->
                            draft.find { draftAccountConfig: DraftAccountConfig ->
                                draftAccountConfig.id == companionId
                            }
                        }
                        ?.let { draftAccountConfig: DraftAccountConfig ->
                            currentRealAccount.setDraftCompanion(draftAccountConfig.toDraftAccount(currentRealAccount))
                        }
                }
        }
        val draftAccounts =
            realAccounts.mapNotNull { realAccount: RealAccount ->
                realAccount.draftCompanion
            }
        return BudgetData(
            generalAccount = generalAccount,
            categoryAccounts = category.map { it.toCategoryAccount() },
            realAccounts = realAccounts,
            draftAccounts = draftAccounts,
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
