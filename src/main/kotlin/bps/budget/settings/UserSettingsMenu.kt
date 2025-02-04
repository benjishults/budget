package bps.budget.settings

import bps.budget.budgetQuitItem
import bps.budget.consistency.updateAnalyticsStartConsistently
import bps.budget.consistency.updateTimeZoneConsistently
import bps.budget.model.BudgetData
import bps.budget.persistence.UserBudgetDao
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.StringValidator
import bps.console.inputs.getTimestampFromUser
import bps.console.io.WithIo
import bps.console.menu.Menu
import bps.console.menu.backItem
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

fun WithIo.userSettingsMenu(
    budgetData: BudgetData,
    userId: UUID,
    userBudgetDao: UserBudgetDao,
) =
    Menu {
        add(
            takeAction({ "Change Time-Zone From ${budgetData.timeZone.id}" }) {
                outPrinter.important(
                    """
        |The Time-Zone is the time-zone in which dates will be presented.
        |When entering a time-zone, it's best to use a format like "Europe/Paris" or "America/New_York" though some other
        |formats are accepted.
                """.trimMargin(),
                )
                changeTimeZone(
                    userId = userId,
                    userBudgetDao = userBudgetDao,
                    budgetData = budgetData,
                )
            },
        )
        val analyticsStartLocalDateTime: LocalDateTime = budgetData.analyticsStart.toLocalDateTime(budgetData.timeZone)
        add(
            takeAction(
                {
                    "Change Analytics Start Date From ${
                        analyticsStartLocalDateTime.toString().substring(0, 10)
                    }"
                },
            ) {
                outPrinter.important(
                    """
        |The Analytics Start Date is the earliest date that analytics will be applied to.
        |Generally, you want it to be the first day of a month occurring after you were using this program diligently to track your budget.
                """.trimMargin(),
                )
                changeAnalyticsStartDate(
                    currentAnalyticsStart = analyticsStartLocalDateTime,
                    timeZone = budgetData.timeZone,
                    budgetId = budgetData.id,
                    userId = userId,
                    userBudgetDao = userBudgetDao,
                    budgetData = budgetData,
                )
            },
        )
        add(backItem)
        add(budgetQuitItem)
    }

fun WithIo.changeTimeZone(
    budgetData: BudgetData,
    userId: UUID,
    userBudgetDao: UserBudgetDao,
) {
    val previousTimeZone: TimeZone = budgetData.timeZone
    val budgetId: UUID = budgetData.id
    outPrinter.verticalSpace()
    SimplePromptWithDefault(
        basicPrompt = "Enter new desired time-zone for dates to be presented in [${previousTimeZone.id}]: ",
        defaultValue = previousTimeZone,
        inputReader = inputReader,
        outPrinter = outPrinter,
        additionalValidation = object : StringValidator {
            override val errorMessage: String = "Must enter a valid time-zone."
            override fun invoke(entry: String): Boolean =
                entry in TimeZone.availableZoneIds
        },
    ) { TimeZone.of(it) }
        .getResult()
        ?.let { newTimeZone: TimeZone ->
            if (updateTimeZoneConsistently(
                    newTimeZone = newTimeZone,
                    userId = userId,
                    budgetId = budgetId,
                    userBudgetDao = userBudgetDao,
                    budgetData = budgetData,
                ) == 1
            ) {
                outPrinter.important("Time-Zone set to $newTimeZone")
                updateAnalyticsStartConsistently(
                    newAnalyticsStart =
                        budgetData
                            .analyticsStart
                            .toLocalDateTime(previousTimeZone)
                            .toInstant(newTimeZone),
                    userId = userId,
                    budgetId = budgetId,
                    userBudgetDao = userBudgetDao,
                    budgetData = budgetData,
                )
            } else {
                outPrinter.important("Failed to change time-zone.  Problem with the database.")
            }
        }
}

private fun WithIo.changeAnalyticsStartDate(
    currentAnalyticsStart: LocalDateTime,
    timeZone: TimeZone,
    budgetId: UUID,
    userId: UUID,
    userBudgetDao: UserBudgetDao,
    budgetData: BudgetData,
) {
    outPrinter.verticalSpace()
    getTimestampFromUser(
        queryAcceptDefault = "Accept the current analytics start date of ${currentAnalyticsStart} [Y]? ",
        default = currentAnalyticsStart,
        dateOnly = true,
    )
        ?.toInstant(timeZone)
        ?.let { newAnalyticsStart: Instant ->
            if (updateAnalyticsStartConsistently(
                    userId = userId,
                    budgetId = budgetId,
                    newAnalyticsStart = newAnalyticsStart,
                    userBudgetDao = userBudgetDao,
                    budgetData = budgetData,
                ) >= 1
            )
                outPrinter.important("Analytics Start Date set to $newAnalyticsStart")
            else
                outPrinter.important("Failed to update Analytics Start Date.  Problem in the database.")
        }
}
