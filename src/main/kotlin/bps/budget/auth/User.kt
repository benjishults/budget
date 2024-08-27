package bps.budget.auth

import kotlinx.datetime.TimeZone
import java.util.UUID

data class User(
    val id: UUID,
    val login: String,
    val access: List<BudgetAccess> = emptyList(),
)

data class BudgetAccess(
    val budgetId: UUID,
    val budgetName: String,
    val timeZone: TimeZone,
    val coarseAccess: CoarseAccess? = null,
    // NOTE currently unused
    val fineAccess: List<FineAccess> = emptyList(),
)

// NOTE currently unused
enum class FineAccess {
    read__transactions,
    create__transactions,
    create__real_accounts,
    create__category_accounts,
    create__draft_accounts,
    share,
    edit__real_accounts__name,
    edit__category_accounts__name,
    edit__draft_accounts__name,
    edit__real_accounts__balance,
    edit__category_accounts__balance,
    edit__draft_accounts__balance,
}

// NOTE currently unused
enum class CoarseAccess {
    view,
    transactions,
    admin,
}


