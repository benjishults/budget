package bps.budget.income

import bps.budget.analytics.AnalyticsOptions
import bps.budget.consistency.commitTransactionConsistently
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.Transaction.Type
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.persistence.AnalyticsDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserConfiguration
import bps.budget.transaction.showRecentRelevantTransactions
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.PositiveStringValidator
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

fun WithIo.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    transactionDao: TransactionDao,
    analyticsDao: AnalyticsDao,
    userConfig: UserConfiguration,
    clock: Clock,
    // TODO make this take the amount first and distribute among accounts?
): Menu {
    val now = clock.now()
    return ScrollingSelectionMenu(
        header = {
            val averageIncome = analyticsDao.averageIncome(
                budgetData.timeZone,
                AnalyticsOptions(
                    excludeFutureUnits = true,
                    excludeCurrentUnit = true,
                    excludePreviousUnit = true,
                    since = budgetData.analyticsStart,
                ),
                budgetData.id,
            )
            val max: BigDecimal? = analyticsDao.maxIncome()
            val min: BigDecimal? = analyticsDao.minIncome()
            String.format(
                """
                    |Select account receiving the INCOME
                    |    Account         |    Balance |    Average |        Max |        Min | Description
                    |    Total Income    |        N/A | ${
                    if (averageIncome === null)
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
                } | Total Monthly Income
                """.trimMargin(),
                averageIncome,
                max,
                min,
            )
        },
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.realAccounts + budgetData.chargeAccounts,
        labelGenerator = {
            val ave = analyticsDao.averageIncome(
                this,
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
                budgetData.id,
            )
            val max = analyticsDao.maxIncome()
            val min = analyticsDao.minIncome()
            formatAccountAnalyticsLabel(ave, max, min)
        },
    ) { _: MenuSession, realAccount: RealAccount ->
        outPrinter.verticalSpace()
        showRecentRelevantTransactions(
            transactionDao = transactionDao,
            account = realAccount,
            budgetData = budgetData,
            label = "Recent income:",
        ) { transactionItem ->
            budgetData.generalAccount in
                    transactionItem.transaction(
                        budgetData.id,
                        budgetData.accountIdToAccountMap,
                    )
                        .categoryItems
                        .map { it.account }
        }
        outPrinter.verticalSpace()
        val amount: BigDecimal =
            SimplePrompt(
                "Enter the AMOUNT of INCOME into '${realAccount.name}': ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                validator = PositiveStringValidator,
            ) {
                // NOTE the validator ensures this is not null
                it.toCurrencyAmountOrNull()!!
            }
                .getResult()
                ?: throw TryAgainAtMostRecentMenuException("No amount entered.")
        outPrinter.verticalSpace()
        if (amount <= BigDecimal.ZERO.setScale(2)) {
            outPrinter.important("Not recording non-positive income.")
        } else {
            outPrinter.verticalSpace()
            val description: String =
                SimplePromptWithDefault(
                    "Enter DESCRIPTION of income [income into '${realAccount.name}']: ",
                    defaultValue = "income into '${realAccount.name}'",
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                )
                    .getResult()
                    ?: throw TryAgainAtMostRecentMenuException("No description entered.")
            outPrinter.verticalSpace()
            val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
                ?.toInstant(budgetData.timeZone)
                ?: throw TryAgainAtMostRecentMenuException("No timestamp entered.")
            commitTransactionConsistently(
                createIncomeTransaction(description, timestamp, amount, budgetData, realAccount),
                transactionDao,
                budgetData,
            )
            outPrinter.important("Income recorded")
        }
    }
}

fun Account.formatAccountAnalyticsLabel(
    ave: BigDecimal?,
    max: BigDecimal?,
    min: BigDecimal?,
): String = String.format(
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

fun createIncomeTransaction(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
): Transaction = createTransactionAddingToRealAccountAndGeneral(
    description = description,
    timestamp = timestamp,
    type = Type.income,
    amount = amount,
    budgetData = budgetData,
    realAccount = realAccount,
)

fun createInitialBalanceTransaction(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
): Transaction = createTransactionAddingToRealAccountAndGeneral(
    description = description,
    timestamp = timestamp,
    type = Type.initial,
    amount = amount,
    budgetData = budgetData,
    realAccount = realAccount,
)

fun createTransactionAddingToRealAccountAndGeneral(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
    type: Type,
) = Transaction.Builder(
    description = description,
    timestamp = timestamp,
    type = type,
)
    .apply {
        with(budgetData.generalAccount) {
            addItemBuilderTo(amount)
        }
        with(realAccount) {
            addItemBuilderTo(amount)
        }
    }
    .build()
