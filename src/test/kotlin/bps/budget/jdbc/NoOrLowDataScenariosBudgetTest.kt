package bps.budget.jdbc

import bps.budget.BudgetApplication
import bps.budget.BudgetConfigurations
import bps.budget.makeAllowances
import bps.budget.persistence.jdbc.JdbcDao
import bps.budget.recordIncome
import bps.budget.recordSpending
import bps.budget.setup
import bps.budget.transfer
import bps.budget.ui.ConsoleUiFacade
import bps.budget.useOrPayCreditCards
import bps.budget.writeOrClearChecks
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
                "Enter the description for your \"General\" account [Income is automatically deposited here and allowances are made from here.] ",
                """
                            |Budget!
                            | 1. $recordIncome (i)
                            | 2. $makeAllowances (a)
                            | 3. $recordSpending (s)
                            | 4. View History (v)
                            | 5. $writeOrClearChecks (ch)
                            | 6. $useOrPayCreditCards (cr)
                            | 7. $transfer (x)
                            | 8. $setup (m)
                            | 9. Quit (q)
                            |""".trimMargin(),
                "Enter selection: ",
                """
Quitting

""",
            )
            inputs shouldHaveSize 0
        }

//        val menus = AllMenus(inputReader, outPrinter)
        "! FIX SINCE IT'S USING NON-JDBC CONFIG: budget with no starting data" {
//            inputs.addAll(
//                listOf("", "", "8"),
//            )
//            val configurations = BudgetConfigurations(sequenceOf("noData.yml"))
//            JdbcDao(configurations.persistence.jdbc!!).use { jdbcDao: JdbcDao ->
//
//                val uiFunctions = ConsoleUiFunctions(inputReader, outPrinter)
//                val budgetMenu = menus.budgetMenu(
//                    BudgetData(configurations.persistence, uiFunctions, jdbcDao),
//                    jdbcDao,
//                )
//                MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
//                    .use {
//                        it.run()
//                    }
//                outputs shouldContainExactly listOf(
//                    """Looks like this is your first time running Budget.
//Enter the name for your "General" account [General] """,
//                    "Enter the description for your \"General\" account [Income is automatically deposited here and allowances are made from here.] ",
//                    """
//                        |Budget!
//                        | 1. Record Income (i)
//                        | 2. Make Allowances
//                        | 3. $spendMoneyItemLabel
//                        | 4. Write Checks or Use Credit Cards
//                        | 5. Clear Drafts
//                        | 6. Transfer Money
//                        | 7. Customize
//                        | 8. Quit (q)
//                        |""".trimMargin(),
//                    "Enter selection: ",
//                    """
//            Quitting
//
//            """,
//                )
//                inputs shouldHaveSize 0
//            }
        }

        "! FIX SINCE IT'S USING NON-JDBC CONFIG: budget with starting account.yml" {
//            inputs.addAll(listOf("7", "5"))
//            val configurations = BudgetConfigurations(sequenceOf("hasGeneralAccount.yml"))
//            val budgetDao = JdbcDao(configurations.persistence.jdbc!!).use { jdbcDao: JdbcDao ->
//
//                val uiFunctions = ConsoleUiFunctions(inputReader, outPrinter)
//                val budgetMenu = menus.budgetMenu(
//                    BudgetData(configurations.persistence, uiFunctions, jdbcDao),
//                    jdbcDao,
//                )
//                MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
//                    .use {
//                        it.run()
//                    }
//                outputs shouldContainExactly listOf(
//                    """
//                        |Budget!
//                        | 1. Record Income (i)
//                        | 2. Make Allowances
//                        | 3. $spendMoneyItemLabel
//                        | 4. Write Checks or Use Credit Cards
//                        | 5. Clear Drafts
//                        | 6. Transfer Money
//                        | 7. Customize
//                        | 8. Quit (q)
//                        |""".trimMargin(),
//                    "Enter selection: ",
//                    """The user must be able to add/remove accounts and categorize accounts (category fund account, real fund account, etc.)
//The user may change the information associated with some account.
//The user may associate a drafts account with a checking account and vice-versa.""",
//                    """
//                        |Customize!
//                        | 1. Create Category Fund
//                        | 2. Create Real Fund
//                        | 3. Create Draft Fund
//                        | 4. Back (b)
//                        | 5. Quit (q)
//                        |""".trimMargin(),
//                    "Enter selection: ",
//                    """
//Quitting
//
//""",
//                )
//                inputs shouldHaveSize 0
//            }
        }

        afterEach {
            JdbcDao(configurations.persistence.jdbc!!)
                .use {
                    dropTables(it.connection, it.config.schema)
                }
        }

    }

}
