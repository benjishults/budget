package bps.budget.transaction

import bps.budget.WithIo
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.console.app.MenuSession
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu

fun WithIo.manageTransactions(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = { "Select account to manage transactions" },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = buildList {
            add(budgetData.generalAccount)
            addAll(budgetData.categoryAccounts - budgetData.generalAccount)
            addAll(budgetData.realAccounts)
            addAll(budgetData.chargeAccounts)
        },
        // TODO https://github.com/benjishults/budget/issues/7
//        extraItems = listOf(item("View Inactive Accounts") {}),
        labelGenerator = {
            String.format("%,10.2f | %-15s | %s", balance, name, description)
        },
    ) { menuSession: MenuSession, selectedAccount: Account ->
        menuSession.push(
            ManageTransactionsMenu(
                account = selectedAccount,
                limit = userConfig.numberOfItemsInScrollingList,
                budgetDao = budgetDao,
                budgetId = budgetData.id,
                accountIdToAccountMap = budgetData.accountIdToAccountMap,
                timeZone = budgetData.timeZone,
                outPrinter = outPrinter,
            ),
        )
    }
