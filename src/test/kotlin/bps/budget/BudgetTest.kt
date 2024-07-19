package bps.budget

import bps.budget.data.BudgetData
import bps.budget.ui.ConsoleUiFunctions
import bps.console.MenuApplication
import bps.console.MenuApplicationWithQuit
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize

class BudgetTest : FreeSpec() {


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
        val uiFunctions = ConsoleUiFunctions(inputReader, outPrinter)
        val menus = AllMenus(inputReader, outPrinter)
        val budgetMenu = menus.budgetMenu
        beforeEach {
            inputs.clear()
            outputs.clear()
        }

        "!budget with no starting data saves account.yml" {
            inputs.addAll(
                listOf("", "", "8"),
            )
            val configurations = BudgetConfigurations(sequenceOf("noData.yml"))
            BudgetData(configurations.persistence, uiFunctions)
            val application: MenuApplication = MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
            application.run()
            outputs shouldContainExactly listOf(
                """Looks like this is your first time running Budget.
Enter the name for your "General" account [General] """,
                "Enter the description for your \"General\" account [Income is automatically deposited here and allowances are made from here.] ",
                """
                        |Budget!
                        | 1. Record Income
                        | 2. Make Allowances
                        | 3. Record Transactions
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

        "budget with no starting data" {
            inputs.addAll(
                listOf("", "", "8"),
            )
            val configurations = BudgetConfigurations(sequenceOf("noData.yml"))
            BudgetData(configurations.persistence, uiFunctions)
            val application: MenuApplication = MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
            application.run()
            outputs shouldContainExactly listOf(
                """Looks like this is your first time running Budget.
Enter the name for your "General" account [General] """,
                "Enter the description for your \"General\" account [Income is automatically deposited here and allowances are made from here.] ",
                """
                        |Budget!
                        | 1. Record Income
                        | 2. Make Allowances
                        | 3. Record Transactions
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
            val configurations = BudgetConfigurations(sequenceOf("hasAccount.yml"))
            BudgetData(configurations.persistence, uiFunctions)
            val application: MenuApplication = MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
            application.run()
            outputs shouldContainExactly listOf(
                """
                        |Budget!
                        | 1. Record Income
                        | 2. Make Allowances
                        | 3. Record Transactions
                        | 4. Write Checks or Use Credit Cards
                        | 5. Clear Drafts
                        | 6. Transfer Money
                        | 7. Customize
                        | 8. Quit
                        |""".trimMargin(),
                "Enter selection: ",
                """The user must be able to add/remove accounts and categorize accounts (virtual fund account, real fund account, etc.)
The user may change the information associated with some account.
The user may associate a drafts account with a checking account and vice-versa.""",
                """
                        |Customize!
                        | 1. Create Virtual Fund
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

        "budget not caring about data" {
            val application: MenuApplication = MenuApplicationWithQuit(budgetMenu, inputReader, outPrinter)
            inputs.addAll(listOf("7", "5"))
//            } andThenThrows (AssertionError("to many calls to inputReader"))
            application.run()
            outputs shouldContainExactly listOf(
                """
                        |Budget!
                        | 1. Record Income
                        | 2. Make Allowances
                        | 3. Record Transactions
                        | 4. Write Checks or Use Credit Cards
                        | 5. Clear Drafts
                        | 6. Transfer Money
                        | 7. Customize
                        | 8. Quit
                        |""".trimMargin(),
                "Enter selection: ",
                """The user must be able to add/remove accounts and categorize accounts (virtual fund account, real fund account, etc.)
The user may change the information associated with some account.
The user may associate a drafts account with a checking account and vice-versa.""",
                """
                        |Customize!
                        | 1. Create Virtual Fund
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

fun main() {
//    val uiFunctions = ConsoleUiFunctions()
    val menus = AllMenus()
//    val configurations = BudgetConfigurations(sequenceOf("budget.yml", "~/.config/bps-budget/budget.yml"))
//    val budgetData = BudgetData(configurations.persistence, uiFunctions)
    val topMenu: Menu = menus.budgetMenu
    try {
        MenuApplicationWithQuit(topMenu).run()
    } finally {
//        uiFunctions.saveData(budgetData)
    }
}
