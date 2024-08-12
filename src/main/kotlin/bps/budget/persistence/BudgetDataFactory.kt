package bps.budget.persistence

import bps.budget.model.BudgetData
import bps.budget.model.BudgetData.Companion.withBasicAccounts
import bps.budget.model.CategoryAccount
import bps.budget.ui.UiFacade
import java.util.TimeZone

/**
 * Loads or data from the DAO.  If there is an error getting it from the DAO, offers to create fresh data.
 */
fun budgetDataFactory(
    uiFacade: UiFacade,
    budgetDao: BudgetDao,
): BudgetData =
    try {
        with(budgetDao) {
            prepForFirstLoad()
            load()
        }
    } catch (ex: DataConfigurationException) {
        uiFacade.announceFirstTime()
        val timeZone: TimeZone = uiFacade.getDesiredTimeZone()
        if (uiFacade.userWantsBasicAccounts()) {
            uiFacade.info(
                """You'll be able to rename these accounts and create new accounts later,
                        |but please answer a couple of questions as we get started.""".trimMargin(),
            )
            withBasicAccounts(
                timeZone = timeZone,
                checkingBalance = uiFacade.getInitialBalance(
                    "Checking",
                    "this is any account on which you are able to write checks",
                ),
                walletBalance = uiFacade.getInitialBalance(
                    "Wallet",
                    "this is cash you might carry on your person",
                ),
            )
                .also { budgetData: BudgetData ->
                    uiFacade.info("saving that data...")
                    budgetDao.save(budgetData)
                    uiFacade.info(
                        """
                                    |Saved
                                    |Next, you'll probably want to
                                    |1) create more accounts (Savings, Credit Cards, etc.)
                                    |2) rename the 'Checking' account to specify your bank name
                                    |3) allocate money from your 'General' account into your category accounts
                            """.trimMargin(),
                    )
                }
        } else {
            uiFacade.createGeneralAccount(budgetDao)
                .let { generalAccount: CategoryAccount ->
                    BudgetData(
                        timeZone,
                        generalAccount,
                        listOf(generalAccount),
                    )
                        .also { budgetData: BudgetData ->
                            budgetDao.save(budgetData)
                        }
                }
        }
    }
