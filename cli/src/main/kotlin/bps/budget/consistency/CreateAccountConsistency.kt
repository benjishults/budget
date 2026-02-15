@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.consistency

import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.toCategoryAccount
import bps.budget.model.toChargeAccount
import bps.budget.persistence.AccountDao
import kotlin.uuid.ExperimentalUuidApi

fun createCategoryAccountConsistently(
    name: String,
    description: String,
    accountDao: AccountDao,
    budgetData: BudgetData,
): CategoryAccount? =
    accountDao
        .createCategoryAccount(name, description, budgetId = budgetData.id)
        .toCategoryAccount()!!
        .also { categoryAccount: CategoryAccount ->
            budgetData.addCategoryAccount(categoryAccount)
        }

fun createChargeAccountConsistently(
    name: String,
    description: String,
    accountDao: AccountDao,
    budgetData: BudgetData,
): ChargeAccount? =
    accountDao
        .createChargeAccount(name, description, budgetId = budgetData.id)
        .toChargeAccount()!!
        .also { chargeAccount: ChargeAccount ->
            budgetData.addChargeAccount(chargeAccount)
        }
