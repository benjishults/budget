package bps.budget

import bps.budget.jdbc.NoDataJdbcTestFixture
import bps.budget.model.BudgetData
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.ui.ConsoleUiFacade
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.TimeZone

class BasicSetupInteractionsTest : FreeSpec(), NoDataJdbcTestFixture {

    override val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)

    init {
        val outputs: MutableList<String> = mutableListOf()
        val outPrinter = OutPrinter {
            outputs.add(it)
        }
        val inputs: MutableList<String> = mutableListOf()
        val inputReader = InputReader {
            inputs.removeFirst()
        }
        beforeEach {
            inputs.clear()
            outputs.clear()
        }
        dropAllBeforeEach()
        closeJdbcAfterSpec()
        "setup basic data through console ui" {
            inputs.addAll(
                listOf("", "y", "2000", "100", "9"),
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
                    application.budgetDao.load().asClue { budgetData: BudgetData ->
                        budgetData.categoryAccounts shouldContain budgetData.generalAccount
                        budgetData.categoryAccounts.size shouldBe 10
                    }
                }
            outputs shouldContainExactly listOf(
                "Looks like this is your first time running Budget.\n",
                "Select the time-zone you want dates to appear in:  [${TimeZone.getDefault().id}] ",
                "Would you like me to set up some standard accounts?  You can always change them later.  [Y] ",
                """
                    |You'll be able to rename these accounts and create new accounts later,
                    |but please answer a couple of questions as we get started.
                    |""".trimMargin(),
                "How much do you currently have in account 'Checking'? (this is any account on which you are able to write checks)  [0.00] ",
                "How much do you currently have in account 'Wallet'? (this is cash you might carry on your person)  [0.00] ",
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
                            | 5. $recordDrafts
                            | 6. $clearDrafts
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
