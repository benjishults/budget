package bps.budget.settings

import bps.budget.consistency.createChargeAccountConsistently
import bps.budget.consistency.createRealAccountConsistentlyWithIo
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.toCurrencyAmountOrNull
import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserBudgetDao
import bps.budget.persistence.UserConfiguration
import bps.console.app.MenuSession
import bps.console.app.TryAgainAtMostRecentMenuException
import bps.console.inputs.NonNegativeStringValidator
import bps.console.inputs.NotInListStringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.StringValidator
import bps.console.inputs.TimestampPrompt
import bps.console.inputs.getTimestampFromUser
import bps.console.io.OutPrinter
import bps.console.io.WithIo
import bps.console.menu.Menu
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.backItem
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.min

fun WithIo.userSettingsMenu(
    budgetData: BudgetData,
    userId: UUID,
    userBudgetDao: UserBudgetDao,
    clock: Clock,
) =
    Menu {
        add(
            takeAction("Change Time-Zone From ${budgetData.timeZone.id}") {
                changeTimeZone(
                    currentTimeZone = budgetData.timeZone, budgetId = budgetData.id,
                    userId = userId,
                    userBudgetDao = userBudgetDao,
                )
            },
        )
        add(
            takeAction("Change Analytics Start Date From ${budgetData.analyticsStart}") {
                changeAnalyticsStartDate(
                    currentAnalyticsStart = budgetData.analyticsStart,
                    timeZone = budgetData.timeZone,
                    budgetId = budgetData.id,
                    userId = userId,
                    userBudgetDao = userBudgetDao,
                    clock = clock,
                )
            },
        )
        add(backItem)
        add(quitItem)
    }

fun WithIo.changeTimeZone(
    currentTimeZone: TimeZone,
    budgetId: UUID,
    userId: UUID,
    userBudgetDao: UserBudgetDao,
) {
    SimplePromptWithDefault(
        basicPrompt = "Enter new desired time-zone for date to appear in [${currentTimeZone.id}]: ",
        defaultValue = currentTimeZone,
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
            userBudgetDao.updateTimeZone(
                timeZoneId = newTimeZone.id,
                userId = userId,
                budgetId = budgetId,
            )
        }
}

private fun WithIo.changeAnalyticsStartDate(
    currentAnalyticsStart: Instant,
    timeZone: TimeZone,
    budgetId: UUID,
    userId: UUID,
    userBudgetDao: UserBudgetDao,
    clock: Clock,
) {
    getTimestampFromUser(
        timeZone = timeZone,
        clock = clock,
        dateOnly = true,
    )
        ?.toInstant(timeZone)
        ?.let { newAnalyticsStart: Instant ->
            userBudgetDao.updateAnalyticsStart(
                analyticsStart = newAnalyticsStart,
                userId = userId,
                budgetId = budgetId,
            )
        }
}

private fun WithIo.deactivateAccount(
    budgetData: BudgetData,
    accountDao: AccountDao,
    userConfig: UserConfiguration,
) = Menu({ "What kind af account do you want to deactivate?" }) {
    add(
        pushMenu("Category Account") {
            deactivateCategoryAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu("Real Account") {
            deactivateRealAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu("Charge Account") {
            deactivateChargeAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(
        pushMenu("Draft Account") {
            deactivateDraftAccountMenu(
                budgetData,
                accountDao,
                userConfig.numberOfItemsInScrollingList,
                outPrinter,
            )
        },
    )
    add(backItem)
    add(quitItem)
}

private fun WithIo.createCreditAccount(
    budgetData: BudgetData,
    accountDao: AccountDao,
) {
    val name: String =
        SimplePrompt<String>(
            "Enter a unique name for the new credit card: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NotInListStringValidator(
                accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("No name entered.")
    if (name.isNotBlank()) {
        val description: String =
            SimplePromptWithDefault(
                "Enter a DESCRIPTION for the new credit card: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                defaultValue = name,
            )
                .getResult()
                ?.trim()
                ?: throw TryAgainAtMostRecentMenuException("No description entered.")
        createChargeAccountConsistently(name, description, accountDao, budgetData)
            ?.let {
                outPrinter.important("New credit card account '$name' created")
            }
            ?: outPrinter.important("Unable to save account..")

    }
}

private fun WithIo.createRealFund(
    budgetData: BudgetData,
    accountDao: AccountDao,
    transactionDao: TransactionDao,
    clock: Clock,
) {
    val name: String =
        SimplePrompt<String>(
            "Enter a unique name for the real account: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validator = NotInListStringValidator(
                accountDao.getAllAccountNamesForBudget(budgetData.id),
                "an existing account name",
            ),
        )
            .getResult()
            ?.trim()
            ?: throw TryAgainAtMostRecentMenuException("Description for the new account not entered.")
    if (name.isNotBlank()) {
        val accountDescription: String =
            SimplePromptWithDefault(
                "Enter a DESCRIPTION for the real account: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                defaultValue = name,
            )
                .getResult()
                ?.trim()
                ?: throw TryAgainAtMostRecentMenuException("Description for the new account not entered.")
        val isDraft: Boolean = SimplePromptWithDefault(
            "Will you write checks on this account [y/N]? ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            defaultValue = false,
        ) { it.trim() in listOf("Y", "y", "true", "yes") }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("No decision made on whether you are going to write checks on this account.")
        val balance: BigDecimal = SimplePromptWithDefault(
            basicPrompt = "Initial balance on account [0.00]:  (This amount will be added to your General account as well.) ",
            defaultValue = BigDecimal.ZERO.setScale(2),
            additionalValidation = NonNegativeStringValidator,
            inputReader = inputReader,
            outPrinter = outPrinter,
        ) {
            it.toCurrencyAmountOrNull() ?: throw IllegalArgumentException("$it is an invalid account balance")
        }
            .getResult()
            ?: throw TryAgainAtMostRecentMenuException("Invalid account balance")
        createRealAccountConsistentlyWithIo(
            name,
            accountDescription,
            balance,
            isDraft,
            transactionDao,
            accountDao,
            budgetData,
            clock,
        )
    }
}

fun deactivateCategoryAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
        limit,
        outPrinter,
    ) {
        (budgetData.categoryAccounts - budgetData.generalAccount)
            .filter {
                it.balance == BigDecimal.ZERO.setScale(2)
            }
    }

fun deactivateRealAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
        limit,
        outPrinter,
    ) {
        budgetData.realAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deactivateChargeAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
        limit,
        outPrinter,
    ) {
        budgetData.chargeAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun deactivateDraftAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
): Menu =
    deactivateAccountMenu(
        budgetData,
        accountDao,
        limit,
        outPrinter,
    ) {
        budgetData.draftAccounts.filter { it.balance == BigDecimal.ZERO.setScale(2) }
    }

fun <T : Account> deactivateAccountMenu(
    budgetData: BudgetData,
    accountDao: AccountDao,
    limit: Int,
    outPrinter: OutPrinter,
    deleteFrom: () -> List<T>,
): Menu =
    ScrollingSelectionMenu(
        header = { "Select account to deactivate" },
        limit = limit,
        itemListGenerator = { lim, offset ->
            val baseList = deleteFrom()
            baseList.subList(
                offset,
                min(baseList.size, offset + lim),
            )
        },
        labelGenerator = { String.format("%,10.2f | %-15s | %s", balance, name, description) },
    ) { _: MenuSession, account: T ->
        budgetData.deleteAccount(account)
        accountDao.deactivateAccount(account)
        outPrinter.important("Deactivated account '${account.name}'")
    }
