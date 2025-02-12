package bps.budget.persistence

import bps.budget.auth.AuthenticatedUser
import bps.budget.model.BudgetData
import bps.budget.ui.UiFacade
import kotlinx.datetime.Clock

/**
 * Loads or initializes data.  If there is an error getting it from the DAO, offers to create fresh data.
 */
fun loadOrBuildBudgetData(
    authenticatedUser: AuthenticatedUser,
    uiFacade: UiFacade,
    budgetDao: BudgetDao,
    budgetName: String,
    clock: Clock,
): BudgetData =
    try {
        loadBudgetData(budgetDao, authenticatedUser, budgetName)
    } catch (ex: Exception) {
        when (ex) {
            is DataConfigurationException, is NoSuchElementException -> {
                uiFacade.firstTimeSetup(budgetName, budgetDao, authenticatedUser, clock)
            }
            else -> throw ex
        }
    }

fun loadBudgetData(
    budgetDao: BudgetDao,
    authenticatedUser: AuthenticatedUser,
    budgetName: String,
): BudgetData =
    with(budgetDao) {
        // FIXME this should have already been called, no?
        prepForFirstLoad()
        load(
            authenticatedUser
                .access
                .first { it.budgetName == budgetName }
                .budgetId,
            authenticatedUser.id,
        )
    }
