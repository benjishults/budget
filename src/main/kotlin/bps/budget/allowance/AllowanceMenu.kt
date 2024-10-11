package bps.budget.allowance

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.menu.Menu
import bps.console.menu.MenuSession
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import java.math.BigDecimal

fun WithIo.makeAllowancesSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = "Select account to allocate money into from ${budgetData.generalAccount.name}: ",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.categoryAccounts - budgetData.generalAccount,
    labelGenerator = { String.format("%,10.2f | %s", balance, name) },
) { menuSession: MenuSession, selectedCategoryAccount: CategoryAccount ->
    val max = budgetData.generalAccount.balance
    val min = BigDecimal.ZERO.setScale(2)
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount to allocate into ${selectedCategoryAccount.name} [$min, $max]: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = { input: String ->
                input
                    .toCurrencyAmountOrNull()
                    ?.let {
                        it in min..max
                    }
                    ?: false
            },
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
    if (amount > BigDecimal.ZERO) {
        val description =
            SimplePromptWithDefault(
                "Enter description of transaction [allowance into ${selectedCategoryAccount.name}]: ",
                defaultValue = "allowance into ${selectedCategoryAccount.name}",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
        val allocate = Transaction.Builder(description, clock.now())
            .apply {
                categoryItemBuilders.add(
                    Transaction.ItemBuilder(
                        -amount,
                        categoryAccount = budgetData.generalAccount,
                    ),
                )
                categoryItemBuilders.add(
                    Transaction.ItemBuilder(
                        amount,
                        categoryAccount = selectedCategoryAccount,
                    ),
                )
            }
            .build()
        budgetData.commit(allocate)
        budgetDao.commit(allocate, budgetData.id)
    }
}
