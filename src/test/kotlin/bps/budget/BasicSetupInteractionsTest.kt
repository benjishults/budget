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
            inputs.addAll(
                listOf("test@test.com", "", "y", "2000", "100", "9"),
            )
            val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)
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
                        budgetData.categoryAccounts.size shouldBe 10
                    }
                    application.budgetDao.load(application.budgetData.id, application.user.id)
                        .asClue { budgetData: BudgetData ->
                            budgetData.categoryAccounts shouldContain budgetData.generalAccount
                            budgetData.categoryAccounts.size shouldBe 10
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
                "saving that data...\n",
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
                            | 1. $recordIncome
                            | 2. $makeAllowances
                            | 3. $recordSpending
                            | 4. View History
                            | 5. $writeOrClearChecks
                            | 6. $useOrPayCreditCards
                            | 7. $transfer
                            | 8. $setup
                            | 9. Quit
                            |""".trimMargin(),
                "Enter selection: ",
                "Quitting\n",
            )
            inputs shouldHaveSize 0

        }
    }

}
