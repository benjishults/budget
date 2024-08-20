package bps.budget.persistence

import bps.budget.auth.User
import bps.budget.model.BudgetData
import bps.budget.ui.UiFacade

/**
 * Loads or initializes data.  If there is an error getting it from the DAO, offers to create fresh data.
 */
fun loadOrBuildBudgetData(
    user: User,
    uiFacade: UiFacade,
    budgetDao: BudgetDao,
    budgetName: String,
): BudgetData =
    try {
        loadBudgetData(budgetDao, user, budgetName)
    } catch (ex: Exception) {
        when (ex) {
            is DataConfigurationException, is NoSuchElementException -> {
                uiFacade.firstTimeSetup(budgetName, budgetDao, user)
            }
            else -> throw ex
        }
    }

fun loadBudgetData(
    budgetDao: BudgetDao,
    user: User,
    budgetName: String,
): BudgetData =
    with(budgetDao) {
        prepForFirstLoad()
        load(
            user
                .access
                .first { it.budgetName == budgetName }
                .budgetId,
            user.id,
        )
    }
