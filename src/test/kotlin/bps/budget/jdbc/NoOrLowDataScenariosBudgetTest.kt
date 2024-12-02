package bps.budget.jdbc

import bps.budget.BudgetApplication
import bps.budget.BudgetConfigurations
import bps.budget.makeAllowancesLabel
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.recordIncomeLabel
import bps.budget.recordSpendingLabel
import bps.budget.manageAccountsLabel
import bps.budget.transferLabel
import bps.budget.ui.ConsoleUiFacade
import bps.budget.useOrPayCreditCardsLabel
import bps.budget.writeOrClearChecksLabel
import bps.console.SimpleConsoleIoTestFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize

class NoOrLowDataScenariosBudgetTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val inputs: MutableList<String> = mutableListOf()
    override val outputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        val configurations = BudgetConfigurations(sequenceOf("noDataJdbc.yml"))
        afterEach {
            JdbcDao(configurations.persistence.jdbc!!)
                .use {
                    dropTables(it.connection, it.config.schema)
                }
        }

        "!budget with no starting data saves general account to db" {
            inputs.addAll(
                listOf("", "", "9"),
            )
            val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)
            BudgetApplication(
                uiFunctions,
                configurations,
                inputReader,
                outPrinter,
            )
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                """Looks like this is your first time running Budget.
Enter the name for your "General" account [General] """,
                "Enter the DESCRIPTION for your \"General\" account [Income is automatically deposited here and allowances are made from here.] ",
                """
                            |Budget!
                            | 1. $recordIncomeLabel (i)
                            | 2. $makeAllowancesLabel (a)
                            | 3. $recordSpendingLabel (s)
                            | 4. View History (t)
                            | 5. $writeOrClearChecksLabel (ch)
                            | 6. $useOrPayCreditCardsLabel (cr)
                            | 7. $transferLabel (x)
                            | 8. $manageAccountsLabel (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                "Enter selection: ",
                """
Quitting

""",
            )
            inputs shouldHaveSize 0
        }

    }

}
