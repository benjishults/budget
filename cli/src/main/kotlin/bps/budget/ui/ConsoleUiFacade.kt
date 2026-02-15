package bps.budget.ui

import bps.budget.UserConfiguration
import bps.budget.model.AccountsHolder
import bps.budget.model.AuthenticatedUser
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.toCategoryAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.UserBudgetDao
import bps.console.app.QuitException
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.StringValidator
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ConsoleUiFacade(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFacade {

    override fun firstTimeSetup(
        budgetName: String,
        accountDao: AccountDao,
        userBudgetDao: UserBudgetDao,
        authenticatedUser: AuthenticatedUser,
        clock: Clock,
    ): BudgetData {
        announceFirstTime()
        val timeZone: TimeZone = getDesiredTimeZone()
        val budgetId: Uuid =
            userBudgetDao
                .createBudget()
                .budgetId
        userBudgetDao.grantAccess(
            budgetName = budgetName,
            timeZoneId = timeZone.id,
            analyticsStart =
                clock
                    .now()
                    .toLocalDateTime(timeZone)
                    .let { now ->
                        if (now.month == Month.DECEMBER) {
                            LocalDateTime(now.year + 1, 1, 1, 0, 0, 0)
                        } else {
                            LocalDateTime(now.year, now.month + 1, 1, 0, 0, 0)
                        }
                    }
                    .toInstant(timeZone),
            userId = authenticatedUser.id,
            budgetId = budgetId,
        )
        return if (userWantsBasicAccounts()) {
            info(
                """
                    |You'll be able to rename these accounts and create new accounts later,
                    |but please answer a couple of questions as we get started.""".trimMargin(),
            )
            BudgetData.persistWithBasicAccounts(
                budgetName = budgetName,
                budgetId = budgetId,
                timeZone = timeZone,
                checkingBalance = getInitialBalance(
                    "Checking",
                    "this is any account on which you are able to write checks",
                ),
                walletBalance = getInitialBalance(
                    "Wallet",
                    "this is cash you might carry on your person",
                ),
                accountDao = accountDao,
            )
                .also {
                    info(
                        """
                                    |Saved
                                    |Next, you'll probably want to
                                    |1) create more accounts (Savings, Credit Cards, etc.)
                                    |2) rename the 'Checking' account to specify your bank name
                                    |3) allocate money from your 'General' account into your category accounts
                            """.trimMargin(),
                    )
                }
        } else {
            accountDao.createGeneralAccount(
                budgetId = Uuid.random(),
            )
                .toCategoryAccount()!!
                .let { generalAccount: CategoryAccount ->
                    BudgetData(
                        id = budgetId,
                        name = budgetName,
                        timeZone = timeZone,
                        analyticsStart = Clock.System.now(),
                        generalAccount = generalAccount,
                        categoryAccounts = AccountsHolder(listOf(generalAccount)),
                    )
                }
        }
    }

    override fun userWantsBasicAccounts(): Boolean =
        SimplePromptWithDefault(
            "Would you like me to set up some standard accounts?  You can always change and rename them later. [Y] ",
            true,
            inputReader,
            outPrinter,
        )
        { it == "Y" || it == "y" || it.isBlank() }
            .getResult()
            ?: throw QuitException()

    override fun announceFirstTime() {
        outPrinter("Looks like this is your first time running Budget.\n")
    }

    override fun getInitialBalance(account: String, description: String): BigDecimal =
        SimplePromptWithDefault(
            "How much do you currently have in account '$account' [0.00]? ",
            BigDecimal.ZERO.setScale(2),
            inputReader,
            outPrinter,
        ) { BigDecimal(it).setScale(2) }
            .getResult()
            ?: throw QuitException()

    override fun getDesiredTimeZone(): TimeZone =
        SimplePromptWithDefault(
            // TODO should this be from the user's config?  Are we even checking that?
            "Select the time-zone you want dates to appear in [${TimeZone.currentSystemDefault().id}]: ",
            TimeZone.currentSystemDefault(),
            inputReader,
            outPrinter,
            additionalValidation = object : StringValidator {
                override val errorMessage: String = "Must enter a valid time-zone."
                override fun invoke(entry: String): Boolean =
                    entry in TimeZone.availableZoneIds
            },
        ) { tzId: String ->
            TimeZone.of(tzId)
        }
            .getResult()
            ?: throw QuitException()

    override fun info(infoMessage: String) {
        outPrinter("$infoMessage\n")
    }

    override fun login(userBudgetDao: UserBudgetDao, userName: String): AuthenticatedUser {
        // TODO replace this with an upsert so we get a single transaction safe from race conditions
        return userBudgetDao
            // NOTE not doing authentication yet
            .getUserByLoginOrNull(userName) as AuthenticatedUser?
            ?: run {
                outPrinter("Unknown user.  Creating new account.")
                AuthenticatedUser(
                    login = userName,
                    id = userBudgetDao.createUser(userName, "a").userId,
                )
            }
    }

//    override fun selectBudget(access: List<BudgetAccess>): String =
//        SelectionPrompt(
//            header = null,
//            options = access.map { it.budgetName },
//            prompt = "Select budget: ",
//            inputReader = inputReader,
//            outPrinter = outPrinter,
//        )
//            .getResult()

    override fun getBudgetName(): String =
        SimplePromptWithDefault(
            basicPrompt = "Enter a name for your budget (only you will see this) [My Budget]:",
            "My Budget",
            inputReader = inputReader,
            outPrinter = outPrinter,
        )
            .getResult()
            .also {
                outPrinter("We recommend you add this to your config file following the directions in the help.\n")
            }
            ?: throw QuitException("No budget name entered.")


}
