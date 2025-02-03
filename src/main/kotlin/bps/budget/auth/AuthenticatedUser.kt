package bps.budget.auth

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.util.UUID

interface User {
    val id: UUID
    val login: String
}

data class AuthenticatedUser(
    override val id: UUID,
    override val login: String,
    val access: List<UserBudgetAccess> = emptyList(),
) : User

data class UserBudgetAccess(
    val budgetId: UUID,
    val budgetName: String,
    val timeZone: TimeZone,
    val analyticsStart: Instant,
    val coarseAccess: CoarseAccess? = null,
    // NOTE currently unused
    val fineAccess: List<FineAccess> = emptyList(),
)

// NOTE currently unused
enum class FineAccess {
    read__transactions,
    create__transactions,
    create__real_accounts,
    create__charge_accounts,
    create__category_accounts,
    create__draft_accounts,
    share,
    edit__real_accounts__name,
    edit__charge_accounts__name,
    edit__category_accounts__name,
    edit__draft_accounts__name,
    edit__real_accounts__balance,
    edit__charge_accounts__balance,
    edit__category_accounts__balance,
    edit__draft_accounts__balance,
}

// NOTE currently unused
enum class CoarseAccess {
    view,
    transactions,
    admin,
}


