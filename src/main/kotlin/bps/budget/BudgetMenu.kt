package bps.budget

import bps.budget.allowance.makeAllowancesSelectionMenu
import bps.budget.charge.creditCardMenu
import bps.budget.checking.checksMenu
import bps.budget.account.manageAccountsMenu
import bps.budget.income.recordIncomeSelectionMenu
import bps.budget.model.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.spend.recordSpendingMenu
import bps.budget.transaction.manageTransactions
import bps.budget.transfer.transferMenu
import bps.console.io.WithIo
import bps.console.menu.Menu
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeActionAndPush
import kotlinx.datetime.Clock

fun WithIo.budgetMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu =
    Menu({ "Budget!" }) {
        add(
            takeActionAndPush(
                label = recordIncomeLabel,
                shortcut = "i",
                to = { recordIncomeSelectionMenu(budgetData, budgetDao.transactionDao, userConfig, clock) },
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
                label = makeAllowancesLabel,
                shortcut = "a",
                to = { makeAllowancesSelectionMenu(budgetData, budgetDao.transactionDao, userConfig, clock) },
            ) {
                outPrinter(
                    "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                )
            },
        )
        add(
            pushMenu(recordSpendingLabel, "s") {
                recordSpendingMenu(budgetData, budgetDao.transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu(manageTransactionsLabel, "t") {
                manageTransactions(budgetData, budgetDao.transactionDao, budgetDao.accountDao, userConfig)
            },
        )
        add(
            pushMenu(writeOrClearChecksLabel, "ch") {
                checksMenu(budgetData, budgetDao.transactionDao, budgetDao.accountDao, userConfig, clock)
            },
        )
        add(
            pushMenu(useOrPayCreditCardsLabel, "cr") {
                creditCardMenu(budgetData, budgetDao.transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu(transferLabel, "x") {
                transferMenu(budgetData, budgetDao.transactionDao, userConfig, clock)
            },
        )
        add(
            pushMenu(manageAccountsLabel, "m") {
                manageAccountsMenu(budgetData, budgetDao, userConfig, clock)
            },
        )
//        add(
//            pushMenu(managePreferencesLabel, "p") {
//                manageSettingsMenu(budgetData, budgetDao, userConfig, clock)
//            },
//        )
        add(quitItem)
    }

