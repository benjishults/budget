package bps.budget.allowance

import bps.console.io.WithIo
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.persistence.AnalyticsDao
import bps.budget.transaction.showRecentRelevantTransactions
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
    transactionDao: TransactionDao,
    analyticsDao: AnalyticsDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu {
    return ScrollingSelectionMenu(
        header = {
            String.format(
                """
                    |Select account to ALLOCATE money into from '%s' [$%,.2f]
                    |    Account         |    Balance |    Average |        Max |        Min | Description
                """.trimMargin(),
                budgetData.generalAccount.name,
                budgetData.generalAccount.balance,
            )
        },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.categoryAccounts - budgetData.generalAccount,
        labelGenerator = {
            val ave = analyticsDao.averageExpenditure(this, budgetData.timeZone)
            val max = analyticsDao.maxExpenditure()
            val min = analyticsDao.minExpenditure()
            String.format(
                "%-15s | %,10.2f | ${
                    if (ave === null)
                        "       N/A"
                    else
                        "%,10.2f"
                } | ${
                    if (max === null)
                        "       N/A"
                    else
                        "%4$,10.2f"
                } | ${
                    if (min === null)
                        "       N/A"
                    else
                        "%5$,10.2f"
                } | %6\$s",
                name,
                balance,
                ave,
                max,
                min,
                description,
            )
        },
    ) { _: MenuSession, selectedCategoryAccount: CategoryAccount ->

        showRecentRelevantTransactions(
            transactionDao = transactionDao,
            account = selectedCategoryAccount,
            budgetData = budgetData,
            label = "Recent allowances:",
        ) { transactionItem ->
            budgetData.generalAccount in
                    transactionItem.transaction(
                        budgetData.id,
                        budgetData.accountIdToAccountMap,
                    )
                        .categoryItems
                        .map { it.account }
        }

        val max = budgetData.generalAccount.balance
        val min = BigDecimal("0.01").setScale(2)
        val amount: BigDecimal =
            SimplePrompt<BigDecimal>(
                "Enter the AMOUNT to ALLOCATE into '${selectedCategoryAccount.name}' [$min, $max]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = InRangeInclusiveStringValidator(min, max),
            ) {
                // NOTE in SimplePrompt, this is only called if the validator passes and in this case, the validator
                //    guarantees that this is not null
                it.toCurrencyAmountOrNull()!!
            }
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
        if (amount > BigDecimal.ZERO) {
            val description: String =
                SimplePromptWithDefault(
                    "Enter DESCRIPTION of transaction [allowance into '${selectedCategoryAccount.name}']: ",
                    defaultValue = "allowance into '${selectedCategoryAccount.name}'",
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                )
                    .getResult()
                    ?: throw TryAgainAtMostRecentMenuException("No description entered.")
            val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
                ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
            val allocate = Transaction.Builder(
                description = description,
                timestamp = timestamp,
                type = Transaction.Type.allowance,
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
            commitTransactionConsistently(allocate, transactionDao, budgetData)
            outPrinter.important("Allowance recorded")
        } else {
            outPrinter.important("Must allow a positive amount.")
        }
    }
}
