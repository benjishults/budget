@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.allowance

import bps.budget.UserConfiguration
import bps.budget.analytics.AnalyticsOptions
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.income.formatAccountAnalyticsLabel
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.Transaction
import bps.budget.model.TransactionType
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AccountTransactionEntity
import bps.budget.persistence.AnalyticsDao
import bps.budget.persistence.TransactionDao
import bps.budget.transaction.showRecentRelevantTransactions
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.InRangeInclusiveStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.io.WithIo
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import kotlin.uuid.ExperimentalUuidApi

fun WithIo.makeAllowancesSelectionMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    accountDao: AccountDao,
    analyticsDao: AnalyticsDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu {
    val now = clock.now()
    return ScrollingSelectionMenu(
        header = {
            val averageExpenditure = analyticsDao.averageExpenditure(
                budgetData.timeZone,
                AnalyticsOptions(
                    excludeFutureUnits = true,
                    excludeCurrentUnit = true,
                    excludePreviousUnit = true,
                    since = budgetData.analyticsStart,
                ),
                budgetData.id,
            )
            val max: BigDecimal? = analyticsDao.maxExpenditure()
            val min: BigDecimal? = analyticsDao.minExpenditure()
            String.format(
                """
                    |Select account to ALLOCATE money into from '%s' [$%,.2f]
                    |    Account         |    Balance |    Average |        Max |        Min | Description
                    |    Total Spend     |        N/A | ${
                    if (averageExpenditure === null)
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
                } | Total Monthly Expenditures
                """.trimMargin(),
                budgetData.generalAccount.name,
                budgetData.generalAccount.balance,
                averageExpenditure,
                max,
                min,
            )
        },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.categoryAccounts - budgetData.generalAccount,
        labelGenerator = {
            val ave = analyticsDao.averageExpenditure(
                this.id,
                budgetData.timeZone,
                AnalyticsOptions(
//            excludeFirstActiveUnit = true,
//            excludeMaxAndMin = false,
//            minimumUnits = 3,
//            timeUnit = DateTimeUnit.MONTH,
                    excludeFutureUnits = true,
                    excludeCurrentUnit = true,
                    // NOTE after the 20th, we count the previous month in analytics
                    // TODO make this configurable
                    excludePreviousUnit =
                        now
                            .toLocalDateTime(budgetData.timeZone)
                            .dayOfMonth < 20,
                    since = budgetData.analyticsStart,
                ),
                budgetId
            )
            val max = analyticsDao.maxExpenditure()
            val min = analyticsDao.minExpenditure()
            formatAccountAnalyticsLabel(ave, max, min)
        },
    ) { _: MenuSession, selectedCategoryAccount: CategoryAccount ->
        outPrinter.verticalSpace()
        showRecentRelevantTransactions(
            transactionDao = transactionDao,
            account = selectedCategoryAccount,
            budgetData = budgetData,
            label = "Recent allowances:",
        ) { transactionItem: AccountTransactionEntity ->
            transactionItem.transactionType in listOf(
                TransactionType.allowance.name,
                TransactionType.expense.name,
                TransactionType.transfer.name,
            )
        }

        val max = budgetData.generalAccount.balance
        val min = BigDecimal("0.01").setScale(2)
        outPrinter.verticalSpace()
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
            outPrinter.verticalSpace()
            val description: String =
                SimplePromptWithDefault(
                    "Enter DESCRIPTION of transaction [allowance into '${selectedCategoryAccount.name}']: ",
                    defaultValue = "allowance into '${selectedCategoryAccount.name}'",
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                )
                    .getResult()
                    ?: throw TryAgainAtMostRecentMenuException("No description entered.")
            outPrinter.verticalSpace()
            val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
                ?.toInstant(budgetData.timeZone)
                ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
            val allocate = Transaction.Builder(
                description = description,
                timestamp = timestamp,
                transactionType = TransactionType.allowance.name,
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
            commitTransactionConsistently(allocate, transactionDao, accountDao, budgetData)
            outPrinter.important("Allowance recorded")
        } else {
            outPrinter.important("Must allow a positive amount.")
        }
    }
}
