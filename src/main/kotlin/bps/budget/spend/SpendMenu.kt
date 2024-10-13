package bps.budget.spend

import bps.budget.WithIo
import bps.budget.model.BudgetData
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.budget.transaction.allocateSpendingItemMenu
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.menu.Menu
import bps.console.menu.MenuSession
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal

fun WithIo.recordSpendingMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = "Select real account money was spent from.",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts,
    labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
) { menuSession: MenuSession, selectedRealAccount: RealAccount ->
    val max = selectedRealAccount.balance
    val min = BigDecimal.ZERO.setScale(2)
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount spent from '${selectedRealAccount.name}' [$min, $max]: ",
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
        val description: String =
            SimplePromptWithDefault(
                "Enter description of transaction [spending from '${selectedRealAccount.name}']: ",
                defaultValue = "spending from '${selectedRealAccount.name}'",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
        val timestamp: Instant =
            getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
        val transactionBuilder: Transaction.Builder =
            Transaction.Builder(description, timestamp)
                .apply {
                    realItemBuilders.add(
                        Transaction.ItemBuilder(
                            amount = -amount,
                            description = description,
                            realAccount = selectedRealAccount,
                        ),
                    )
                }
        menuSession.push(
            allocateSpendingItemMenu(
                amount,
                transactionBuilder,
                description,
                budgetData,
                budgetDao,
                userConfig,
            ),
        )
    }
}
