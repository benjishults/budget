package bps.budget.file

import bps.budget.AllMenus
import bps.budget.BudgetApplication
import bps.budget.BudgetConfigurations
import bps.budget.budgetMenu
import bps.budget.data.BudgetData
import bps.budget.persistence.files.FilesDao
import bps.budget.spendMoneyItemLabel
import bps.budget.ui.ConsoleUiFunctions
import bps.config.convertToPath
import bps.console.MenuApplicationWithQuit
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File

class NoOrLowDataScenariosBudgetTest : FreeSpec() {


//    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerTest

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

        "budget with no starting data saves account.yml" {
            inputs.addAll(
                listOf("", "", "8"),
            )
            val configurations = BudgetConfigurations(sequenceOf("noData.yml"))
            val uiFunctions = ConsoleUiFunctions(inputReader, outPrinter)
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
                        | 1. Record Income
                        | 2. Make Allowances
                        | 3. $spendMoneyItemLabel
                        | 4. Write Checks or Use Credit Cards
                        | 5. Clear Drafts
                        | 6. Transfer Money
                        | 7. Customize
                        | 8. Quit
                        |""".trimMargin(),
                "Enter selection: ",
                "Quitting\n",
            )
            inputs shouldHaveSize 0
            File(convertToPath(configurations.persistence.file!!.dataDirectory)).deleteContentsOfNonEmptyFolder() shouldBe true
        }

        val menus = AllMenus(inputReader, outPrinter)
        "budget with no starting data" {
            inputs.addAll(
                listOf("", "", "8"),
            )
            val configurations = BudgetConfigurations(sequenceOf("noData.yml"))
            val budgetDao = FilesDao(configurations.persistence.file!!)
            val uiFunctions = ConsoleUiFunctions(inputReader, outPrinter)
            val budgetMenu = menus.budgetMenu(
                BudgetData(configurations.persistence, uiFunctions, budgetDao),
                budgetDao,
            )
            MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                """Looks like this is your first time running Budget.
Enter the name for your "General" account [General] """,
                "Enter the description for your \"General\" account [Income is automatically deposited here and allowances are made from here.] ",
                """
                        |Budget!
                        | 1. Record Income
                        | 2. Make Allowances
                        | 3. $spendMoneyItemLabel
                        | 4. Write Checks or Use Credit Cards
                        | 5. Clear Drafts
                        | 6. Transfer Money
                        | 7. Customize
                        | 8. Quit
                        |""".trimMargin(),
                "Enter selection: ",
                "Quitting\n",
            )
            inputs shouldHaveSize 0
        }

        "budget with starting account.yml" {
            inputs.addAll(listOf("7", "5"))
            val configurations = BudgetConfigurations(sequenceOf("hasGeneralAccount.yml"))
            val budgetDao = FilesDao(configurations.persistence.file!!)
            val uiFunctions = ConsoleUiFunctions(inputReader, outPrinter)
            val budgetMenu = menus.budgetMenu(
                BudgetData(configurations.persistence, uiFunctions, budgetDao),
                budgetDao,
            )
            MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
                .use {
                    it.run()
                }
            outputs shouldContainExactly listOf(
                """
                        |Budget!
                        | 1. Record Income
                        | 2. Make Allowances
                        | 3. $spendMoneyItemLabel
                        | 4. Write Checks or Use Credit Cards
                        | 5. Clear Drafts
                        | 6. Transfer Money
                        | 7. Customize
                        | 8. Quit
                        |""".trimMargin(),
                "Enter selection: ",
                """The user must be able to add/remove accounts and categorize accounts (category fund account, real fund account, etc.)
The user may change the information associated with some account.
The user may associate a drafts account with a checking account and vice-versa.""",
                """
                        |Customize!
                        | 1. Create Category Fund
                        | 2. Create Real Fund
                        | 3. Create Draft Fund
                        | 4. Back
                        | 5. Quit
                        |""".trimMargin(),
                "Enter selection: ",
                "Quitting\n",
            )
            inputs shouldHaveSize 0
        }

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
