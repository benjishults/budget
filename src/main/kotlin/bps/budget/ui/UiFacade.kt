package bps.budget.ui

import bps.budget.auth.BudgetAccess
import bps.budget.auth.User
import bps.budget.model.BudgetData
import bps.budget.model.BudgetData.Companion.withBasicAccounts
import bps.budget.model.CategoryAccount
import bps.budget.model.defaultGeneralAccountDescription
import bps.budget.model.defaultGeneralAccountName
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.console.inputs.CompositePrompt
import bps.console.inputs.SelectionPrompt
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import kotlinx.datetime.TimeZone
import org.apache.commons.validator.routines.EmailValidator
import java.math.BigDecimal
import java.util.UUID

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
            withBasicAccounts(
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
        return CompositePrompt(
            listOf(
                SimplePromptWithDefault(
                    "Enter the name for your \"General\" account [$defaultGeneralAccountName]: ",
                    defaultGeneralAccountName,
                    inputReader,
                    outPrinter,
                ),
                SimplePromptWithDefault(
                    "Enter the description for your \"General\" account [$defaultGeneralAccountDescription]: ",
                    defaultGeneralAccountDescription,
                    inputReader,
                    outPrinter,
                ),
            ),
        )
        {
            CategoryAccount(it[0] as String, it[1] as String)
        }
            .getResult()
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

    override fun getDesiredTimeZone(): TimeZone =
        SimplePromptWithDefault(
            "Select the time-zone you want dates to appear in [${TimeZone.currentSystemDefault().id}]: ",
            TimeZone.currentSystemDefault(),
            inputReader,
            outPrinter,
        ) { TimeZone.of(it) }
            .getResult()

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
                    validate = { EmailValidator.getInstance().isValid(it) },
                )
                    .getResult()
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
            .getUserByLogin(login)
            ?: run {
                outPrinter("Unknown user.  Creating new account.")
                User(
                    login = login,
                    id = budgetDao.createUser(login, "a"),
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


}
