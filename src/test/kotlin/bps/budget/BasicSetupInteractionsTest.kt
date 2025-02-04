package bps.budget

import bps.budget.jdbc.NoDataJdbcTestFixture
import bps.budget.model.BudgetData
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import bps.console.SimpleConsoleIoTestFixture
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone

class BasicSetupInteractionsTest : FreeSpec(),
    NoDataJdbcTestFixture,
    SimpleConsoleIoTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)
    override val inputs: MutableList<String> = mutableListOf()
    override val outputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        dropAllBeforeEach()
        closeJdbcAfterSpec()
        "setup basic data through console ui" {
            val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)
            inputs.addAll(
                listOf("test@test.com", "", "y", "2000", "100", "10"),
            )
            BudgetApplication(
                uiFunctions,
                configurations,
                inputReader,
                outPrinter,
            )
                .use { application: BudgetApplication ->
                    application.run()
                    application.budgetData.asClue { budgetData: BudgetData ->
                        budgetData.categoryAccounts shouldContain budgetData.generalAccount
                        budgetData.categoryAccounts.size shouldBe 14
                    }
                    application.budgetDao.load(application.budgetData.id, application.authenticatedUser.id)
                        .asClue { budgetData: BudgetData ->
                            budgetData.categoryAccounts shouldContain budgetData.generalAccount
                            budgetData.categoryAccounts.size shouldBe 14
                        }
                }
            outputs shouldContainExactly listOf(
                "username: ",
                "Unknown user.  Creating new account.",
                "Looks like this is your first time running Budget.\n",
                "Select the time-zone you want dates to appear in [${TimeZone.currentSystemDefault().id}]: ",
                "Would you like me to set up some standard accounts?  You can always change and rename them later. [Y] ",
                """
                    |You'll be able to rename these accounts and create new accounts later,
                    |but please answer a couple of questions as we get started.
                    |""".trimMargin(),
                "How much do you currently have in account 'Checking' [0.00]? ",
                "How much do you currently have in account 'Wallet' [0.00]? ",
                """
                    |Saved
                    |Next, you'll probably want to
                    |1) create more accounts (Savings, Credit Cards, etc.)
                    |2) rename the 'Checking' account to specify your bank name
                    |3) allocate money from your 'General' account into your category accounts
                    |
                """.trimMargin(),
                """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. $manageTransactionsLabel (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. User Settings (u)
                            |10. Quit (q)
                            |""".trimMargin(),
                "Enter selection: ",
                """
Quitting

Consider running the backup if you are storing the data locally.

""",
            )
            inputs shouldHaveSize 0

        }
    }

}
