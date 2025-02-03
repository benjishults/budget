package bps.budget.ui

import bps.budget.auth.AuthenticatedUser
import bps.budget.model.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserBudgetDao
import bps.budget.persistence.UserConfiguration
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal

interface UiFacade {
    fun userWantsBasicAccounts(): Boolean
    fun announceFirstTime(): Unit
    fun getInitialBalance(account: String, description: String): BigDecimal
    fun getDesiredTimeZone(): TimeZone
    fun info(infoMessage: String)

    /**
     * Guarantees that the user is in the DB and any authentication has been applied
     */
    fun login(userBudgetDao: UserBudgetDao, userConfiguration: UserConfiguration): AuthenticatedUser

    //    fun selectBudget(access: List<BudgetAccess>): String
    fun getBudgetName(): String
    fun firstTimeSetup(
        budgetName: String,
        budgetDao: BudgetDao,
        authenticatedUser: AuthenticatedUser,
        clock: Clock,
    ): BudgetData
}

fun Instant.formatAsLocalDateTime(timeZone: TimeZone): String =
    toLocalDateTime(timeZone)
        .format(
            LocalDateTime.Format {
                date(
                    LocalDate.Format {
                        year()
                        char('-')
                        monthNumber()
                        char('-')
                        dayOfMonth()
                    },
                )
                char(' ')
                time(
                    LocalTime.Format {
                        hour()
                        char(':')
                        minute()
                        char(':')
                        second()
                    },
                )
            },
        )

fun Instant.formatAsLocalDate(timeZone: TimeZone): String =
    toLocalDateTime(timeZone)
        .format(
            LocalDateTime.Format {
                date(
                    LocalDate.Format {
                        year()
                        char('-')
                        monthNumber()
                        char('-')
                        dayOfMonth()
                    },
                )
            },
        )
