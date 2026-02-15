package bps.budget

import bps.budget.model.AuthenticatedUser
import bps.budget.model.BudgetData
import bps.budget.persistence.AccountDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.UserBudgetDao
import bps.budget.ui.UiFacade
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi

/**
 * Loads or initializes data.  If there is an error getting it from the DAO, offers to create fresh data.
 */
fun loadOrBuildBudgetData(
    authenticatedUser: AuthenticatedUser,
    uiFacade: UiFacade,
    initializingBudgetDao: InitializingBudgetDao,
    cliBudgetDao: CliBudgetDao,
    accountDao: AccountDao,
    userBudgetDao: UserBudgetDao,
    budgetName: String,
    clock: Clock,
): BudgetData =
    try {
        loadBudgetData(initializingBudgetDao, cliBudgetDao, accountDao, authenticatedUser.login, budgetName)
    } catch (ex: Exception) {
        when (ex) {
            is DataConfigurationException, is NoSuchElementException -> {
                uiFacade.firstTimeSetup(budgetName, accountDao, userBudgetDao, authenticatedUser, clock)
            }
            else -> throw ex
        }
    }

@OptIn(ExperimentalUuidApi::class)
fun loadBudgetData(
    initializingBudgetDao: InitializingBudgetDao,
    cliBudgetDao: CliBudgetDao,
    accountDao: AccountDao,
    userName: String,
    budgetName: String,
): BudgetData =
    with(initializingBudgetDao) {
        // FIXME this should have already been called, no?
        ensureTablesAndIndexes()
        cliBudgetDao.load(
            budgetName,
            userName,
            accountDao,
        )
    }
