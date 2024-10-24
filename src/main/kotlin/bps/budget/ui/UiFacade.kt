package bps.budget.ui

import bps.budget.auth.BudgetAccess
import bps.budget.auth.User
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
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
    fun createGeneralAccount(budgetDao: BudgetDao): CategoryAccount
    fun userWantsBasicAccounts(): Boolean
    fun announceFirstTime(): Unit
    fun getInitialBalance(account: String, description: String): BigDecimal
    fun getDesiredTimeZone(): TimeZone
    fun info(infoMessage: String)
    fun login(budgetDao: BudgetDao, userConfiguration: UserConfiguration): User
    fun selectBudget(access: List<BudgetAccess>): String
    fun getBudgetName(): String
    fun firstTimeSetup(budgetName: String, budgetDao: BudgetDao, user: User): BudgetData
}

fun Instant.format(timeZone: TimeZone): String =
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
