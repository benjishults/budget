package bps.budget.file

import bps.budget.BudgetApplication
import bps.budget.BudgetConfigurations
import bps.budget.WithIo
import bps.budget.makeAllowances
import bps.budget.recordIncome
import bps.budget.recordSpending
import bps.budget.setup
import bps.budget.transfer
import bps.budget.ui.ConsoleUiFacade
import bps.budget.useOrPayCreditCards
import bps.budget.writeOrClearChecks
import bps.config.convertToPath
import bps.console.SimpleConsoleIoTestFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

class NoOrLowDataScenariosBudgetTest : FreeSpec(),
    SimpleConsoleIoTestFixture {

    override val outputs: MutableList<String> = mutableListOf()
    override val inputs: MutableList<String> = mutableListOf()

    init {
        clearInputsAndOutputsBeforeEach()
        val clock = object : Clock {
            var secondCount = 0
            override fun now(): Instant =
                Instant.parse(String.format("2024-08-09T00:00:%02dZ", secondCount++))
        }
        "!budget with no starting data saves account.yml" {
            inputs.addAll(
                listOf("", "", "9"),
            )
            val configurations = BudgetConfigurations(sequenceOf("noData.yml"))
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
            File(convertToPath(configurations.persistence.file!!.dataDirectory)).deleteContentsOfNonEmptyFolder() shouldBe true
        }

        val menus = WithIo(inputReader, outPrinter)
//        "!budget with no starting data" {
//            inputs.addAll(
//                listOf("", "", "9"),
//            )
//            val configurations = BudgetConfigurations(sequenceOf("noData.yml"))
//            val budgetDao = FilesDao(configurations.persistence.file!!)
//            val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)
//            val budgetMenu =
//                menus.budgetMenu(
//                    budgetData = buildBudgetData(
//                        uiFacade = uiFunctions,
//                        budgetDao = budgetDao,
//                        userConfiguration = configurations.user,
//                        budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
//                    ).first,
//                    budgetDao = budgetDao,
//                    clock = clock,
//                )
//            MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
//                .use {
//                    it.run()
//                }
//            outputs shouldContainExactly listOf(
//                """Looks like this is your first time running Budget.
//Enter the name for your "General" account [General] """,
//                "Enter the description for your \"General\" account [Income is automatically deposited here and allowances are made from here.] ",
//                """
//                            |Budget!
//                            | 1. $recordIncome (i)
//                            | 2. $makeAllowances (a)
//                            | 3. $recordSpending (s)
//                            | 4. View History (v)
//                            | 5. $recordDrafts
//                            | 6. $clearDrafts
//                            | 7. $transfer (x)
//                            | 8. $setup (m)
//                            | 9. Quit (q)
//                            |""".trimMargin(),
//                "Enter selection: ",
//                """
//Quitting
//
//""",
//            )
//            inputs shouldHaveSize 0
//            File(convertToPath(configurations.persistence.file!!.dataDirectory)).deleteContentsOfNonEmptyFolder() shouldBe true
//        }

//        "!budget with starting account.yml" {
//            inputs.addAll(listOf("7", "5"))
//            val configurations = BudgetConfigurations(sequenceOf("hasGeneralAccount.yml"))
//            val budgetDao = FilesDao(configurations.persistence.file!!)
//            val uiFunctions = ConsoleUiFacade(inputReader, outPrinter)
//            val budgetMenu =
//                menus.budgetMenu(
//                    budgetData = buildBudgetData(
//                        uiFunctions,
//                        budgetDao,
//                        configurations.user,
//                        getBudgetNameFromPersistenceConfig(configurations.persistence)!!,
//                    ).first,
//                    budgetDao = budgetDao,
//                    clock = clock,
//                )
//            MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
//                .use {
//                    it.run()
//                }
//            outputs shouldContainExactly listOf(
//                """
//                            |Budget!
//                            | 1. $recordIncome (i)
//                            | 2. $makeAllowances (a)
//                            | 3. $recordSpending (s)
//                            | 4. View History (v)
//                            | 5. $recordDrafts
//                            | 6. $clearDrafts
//                            | 7. $transfer (x)
//                            | 8. $setup (m)
//                            | 9. Quit (q)
//                            |""".trimMargin(),
//                "Enter selection: ",
//                """The user must be able to add/remove accounts and categorize accounts (category fund account, real fund account, etc.)
//The user may change the information associated with some account.
//The user may associate a drafts account with a checking account and vice-versa.""",
//                """
//                        |Customize!
//                        | 1. Create Category Fund
//                        | 2. Create Real Fund
//                        | 3. Create Draft Fund
//                        | 4. Back (b)
//                        | 5. Quit (q)
//                        |""".trimMargin(),
//                "Enter selection: ",
//                """
//Quitting
//
//""",
//            )
//            inputs shouldHaveSize 0
////            File(convertToPath(configurations.persistence.file!!.dataDirectory)).deleteContentsOfNonEmptyFolder() shouldBe true
//        }

    }

}

/**
 * @return `true` if the receiver
 * 1. exists
 * 2. is a folder
 * 3. is not empty at time of call
 * 4. has all contents deleted on completion of this call
 */
fun File.deleteContentsOfNonEmptyFolder(): Boolean =
    this.exists() &&
            this.isDirectory &&
            this.list()!!.isNotEmpty() &&
            this.walkBottomUp()
                .filter { it != this }
                .all(File::delete)
