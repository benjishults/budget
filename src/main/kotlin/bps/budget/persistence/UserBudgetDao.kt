package bps.budget.persistence

import bps.budget.auth.User
import java.util.UUID

interface UserBudgetDao {
    fun getUserByLoginOrNull(login: String): User? = null
    fun createUser(login: String, password: String, userId: UUID = UUID.randomUUID()): UUID = TODO()
    fun createBudgetOrNull(generalAccountId: UUID, budgetId: UUID = UUID.randomUUID()): UUID?
    fun grantAccess(budgetName: String, timeZoneId: String, userId: UUID, budgetId: UUID)
    fun deleteBudget(budgetId: UUID) {}
    fun deleteUser(userId: UUID) {}
    fun deleteUserByLogin(login: String) {}
}
