@file:OptIn(ExperimentalUuidApi::class)

package bps.budget

import bps.budget.account.manageAccountsMenu
import bps.budget.allowance.makeAllowancesSelectionMenu
import bps.budget.charge.creditCardMenu
import bps.budget.checking.checksMenu
import bps.budget.income.recordIncomeSelectionMenu
import bps.budget.model.BudgetData
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AnalyticsDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserBudgetDao
import bps.budget.settings.userSettingsMenu
import bps.budget.spend.recordSpendingMenu
import bps.budget.transaction.manageTransactions
import bps.budget.transfer.transferMenu
import bps.console.io.WithIo
import bps.console.menu.Menu
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeActionAndPush
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val budgetQuitItem = quitItem(
    """
                                |Quitting
                                |
                                |Consider running the backup if you are storing the data locally.
                        """.trimMargin(),
)

fun WithIo.budgetMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    analyticsDao: AnalyticsDao,
    userBudgetDao: UserBudgetDao,
    userConfig: UserConfiguration,
    userId: Uuid,
    clock: Clock,
): Menu =
    Menu({ "Budget!" }) {
        add(
            takeActionAndPush(
                label = { recordIncomeLabel },
                shortcut = "i",
                to = {
                    recordIncomeSelectionMenu(
                        budgetData,
                        transactionDao,
                        accountDao,
                        analyticsDao,
                        userConfig,
                        clock,
                    )
                },
            ) {
                outPrinter.important(
                    """
            |Enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money will be automatically entered into the '${budgetData.generalAccount.name}' account.
                    """.trimMargin(),
                )
            },
        )
        add(
            takeActionAndPush(
                label = { makeAllowancesLabel },
                shortcut = "a",
                to = {
                    makeAllowancesSelectionMenu(
                        budgetData,
                        transactionDao,
                        accountDao,
                        analyticsDao,
                        userConfig,
                        clock,
                    )
                },
            ) {
                outPrinter.important(
                    "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                )
            },
        )
        add(
            pushMenu({ recordSpendingLabel }, "s") {
                recordSpendingMenu(budgetData, transactionDao, accountDao, userConfig, clock)
            },
        )
        add(
            pushMenu({ manageTransactionsLabel }, "t") {
                manageTransactions(
                    budgetData,
                    transactionDao,
                    accountDao,
                    userConfig,
                )
            },
        )
        add(
            pushMenu({ writeOrClearChecksLabel }, "ch") {
                checksMenu(
                    budgetData,
                    transactionDao,
                    accountDao,
                    userConfig,
                    clock,
                )
            },
        )
        add(
            pushMenu({ useOrPayCreditCardsLabel }, "cr") {
                creditCardMenu(budgetData, transactionDao, accountDao, userConfig, clock)
            },
        )
        add(
            pushMenu({ transferLabel }, "x") {
                transferMenu(budgetData, transactionDao, accountDao, userConfig, clock)
            },
        )
        add(
            pushMenu({ manageAccountsLabel }, "m") {
                manageAccountsMenu(budgetData, accountDao, transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu({ userSettingsLabel }, "u") {
                userSettingsMenu(
                    budgetData = budgetData,
                    userId = userId,
                    userBudgetDao = userBudgetDao,
                )
            },
        )
//        add(
//            pushMenu(managePreferencesLabel, "p") {
//                manageSettingsMenu(budgetData, budgetDao, userConfig, clock)
//            },
//        )
        add(
            budgetQuitItem,
        )
    }
