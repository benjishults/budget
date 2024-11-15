package bps.budget.allowance

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.InRangeInclusiveStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun WithIo.makeAllowancesSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = {
        String.format(
            "Select account to ALLOCATE money into from '%s' [$%,.2f]",
            budgetData.generalAccount.name,
            budgetData.generalAccount.balance,
        )
    },
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.categoryAccounts - budgetData.generalAccount,
    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
) { _: MenuSession, selectedCategoryAccount: CategoryAccount ->
    val max = budgetData.generalAccount.balance
    val min = BigDecimal("0.01").setScale(2)
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount to ALLOCATE into '${selectedCategoryAccount.name}' [$min, $max]: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = InRangeInclusiveStringValidator(min, max),
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
    if (amount > BigDecimal.ZERO) {
        val description: String =
            SimplePromptWithDefault(
                "Enter description of transaction [allowance into '${selectedCategoryAccount.name}']: ",
                defaultValue = "allowance into '${selectedCategoryAccount.name}'",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No description entered.")
        val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
        val allocate = Transaction.Builder(
            description,
            timestamp,
        )
            .apply {
                with(budgetData.generalAccount) {
                    addItemBuilderTo(-amount)
                }
                with(selectedCategoryAccount) {
                    addItemBuilderTo(amount)
                }
            }
            .build()
        budgetData.commit(allocate)
        budgetDao.transactionDao.commit(allocate, budgetData.id)
        outPrinter.important("Allowance recorded")
    } else {
        outPrinter.important("Must allow a positive amount.")
    }
}
