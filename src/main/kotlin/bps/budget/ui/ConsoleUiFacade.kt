package bps.budget.ui

import bps.budget.auth.BudgetAccess
import bps.budget.auth.User
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.defaultGeneralAccountDescription
import bps.budget.model.defaultGeneralAccountName
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.console.app.QuitException
import bps.console.inputs.EmailStringValidator
import bps.console.inputs.SelectionPrompt
import bps.console.inputs.StringValidator
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID

class ConsoleUiFacade(
    val inputReader: InputReader = DefaultInputReader,
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : UiFacade {

    override fun firstTimeSetup(
        budgetName: String,
        budgetDao: BudgetDao,
        user: User,
    ): BudgetData {
        announceFirstTime()
        val timeZone: TimeZone = getDesiredTimeZone()
        return if (userWantsBasicAccounts()) {
            info(
                """
                    |You'll be able to rename these accounts and create new accounts later,
                    |but please answer a couple of questions as we get started.""".trimMargin(),
            )
            BudgetData.withBasicAccounts(
                budgetName = budgetName,
                timeZone = timeZone,
                checkingBalance = getInitialBalance(
                    "Checking",
                    "this is any account on which you are able to write checks",
                ),
                walletBalance = getInitialBalance(
                    "Wallet",
                    "this is cash you might carry on your person",
                ),
            )
                .also { budgetData: BudgetData ->
                    info("saving that data...")
                    budgetDao.save(budgetData, user)
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
            createGeneralAccount(budgetDao)
                .let { generalAccount: CategoryAccount ->
                    BudgetData(
                        id = UUID.randomUUID(),
                        name = budgetName,
                        timeZone = timeZone,
                        generalAccount = generalAccount,
                        categoryAccounts = listOf(generalAccount),
                    )
                        .also { budgetData: BudgetData ->
                            budgetDao.save(budgetData, user)
                        }
                }
        }
    }


    override fun createGeneralAccount(budgetDao: BudgetDao): CategoryAccount {
        budgetDao.prepForFirstSave()
        val name: String =
            SimplePromptWithDefault(
                "Enter the name for your \"General\" account [$defaultGeneralAccountName]: ",
                defaultGeneralAccountName,
                inputReader,
                outPrinter,
            )
                .getResult()
                ?: throw QuitException()
        val description: String =
            SimplePromptWithDefault(
                "Enter the description for your \"General\" account [$defaultGeneralAccountDescription]: ",
                defaultGeneralAccountDescription,
                inputReader,
                outPrinter,
            )
                .getResult()
                ?: throw QuitException()
        return CategoryAccount(name, description, budgetId = UUID.randomUUID())
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
        ) { TimeZone.of(it) }
            .getResult()
            ?: throw QuitException()

    override fun info(infoMessage: String) {
        outPrinter("$infoMessage\n")
    }

    override fun login(budgetDao: BudgetDao, userConfiguration: UserConfiguration): User {
        val login: String =
            if (userConfiguration.defaultLogin === null) {
                SimplePrompt<String>(
                    "username: ",
                    inputReader = inputReader,
                    outPrinter = outPrinter,
                    validator = EmailStringValidator,
                )
                    .getResult()
                    ?: throw QuitException("No valid email entered.")
            } else {
                // for now, just use the configured one
                userConfiguration.defaultLogin
//                SimplePromptWithDefault(
//                    "username: ",
//                    userConfiguration.defaultLogin,
//                    inputReader = inputReader,
//                    outPrinter = outPrinter,
//                    validate = { it.isNotBlank() && EmailValidator.getInstance().isValid(it) },
//                )
//                    .getResult()
            }
        return budgetDao
            .userBudgetDao
            .getUserByLogin(login)
            ?: run {
                outPrinter("Unknown user.  Creating new account.")
                User(
                    login = login,
                    id = budgetDao.userBudgetDao.createUser(login, "a"),
                )
            }
    }

    override fun selectBudget(access: List<BudgetAccess>): String =
        SelectionPrompt(
            header = null,
            options = access.map { it.budgetName },
            prompt = "Select budget: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
        )
            .getResult()

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
