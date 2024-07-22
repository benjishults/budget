@file:JvmName("Budget")

package bps.budget

import bps.budget.customize.customizeMenu
import bps.budget.data.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount
import bps.budget.transaction.Transaction
import bps.budget.ui.ConsoleUiFunctions
import bps.budget.ui.UiFunctions
import bps.console.MenuApplicationWithQuit
import bps.console.inputs.RecursivePrompt
import bps.console.inputs.SimplePrompt
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.menuBuilder
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import bps.console.menu.takeActionAndPush

fun main(args: Array<String>) {
    val uiFunctions = ConsoleUiFunctions()
    val configurations = BudgetConfigurations(sequenceOf("budget.yml", "~/.config/bps-budget/budget.yml"))
    BudgetApplication(uiFunctions, configurations, uiFunctions.inputReader, uiFunctions.outPrinter)
        .use() {
            it.run()
        }
}

class BudgetApplication private constructor(
    val uiFunctions: UiFunctions,
    val configurations: BudgetConfigurations,
    inputReader: InputReader,
    outPrinter: OutPrinter,
    private val accountsFileName: String,
    val budgetData: BudgetData,
) : MenuApplicationWithQuit(AllMenus().budgetMenu(budgetData), inputReader, outPrinter) {

    constructor(
        uiFunctions: UiFunctions,
        configurations: BudgetConfigurations,
        inputReader: InputReader = DefaultInputReader,
        outPrinter: OutPrinter = DefaultOutPrinter,
        accountsFileName: String = "accounts.yml",
    ) : this(
        uiFunctions,
        configurations,
        inputReader,
        outPrinter,
        accountsFileName,
        BudgetData(configurations.persistence, uiFunctions),
    )

    override fun close() {
        uiFunctions.saveData(configurations.persistence, budgetData, accountsFileName)
    }

}

const val spendMoneyItemLabel = "Record Transactions"

fun AllMenus.budgetMenu(budgetData: BudgetData): Menu =
    menuBuilder("Budget!") {
        add(
            takeAction("Record Income") {
                outPrinter(
                    """
            |The user should enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money should be automatically entered into the general category fund account.""".trimMargin(),
                )
            },
        )
        add(
            takeAction("Make Allowances") {
                outPrinter(
                    """
                |Every month or so, the user may want to distribute the income from the general category fund accounts into the other category fund accounts.
                |Optional: You may want to add options to automate this procedure for the user.
                |I.e., let the user decide on a predetermined amount that will be transferred to each category fund account each month.
                |For some category fund accounts the user may prefer to bring the balance up to a certain amount each month.""".trimMargin(),
                )
            },
        )
        add(
            takeAction(spendMoneyItemLabel) {
                outPrinter(
                    """
                |Optional but recommended:
                |Sometimes one expenditure from a real fund account will have several category funds account associated with it.
                |For example, if I write a check at WalMart, some of that may have been for necessities, some for food, some for school (books), and some for entertainment.
                |The user interface should allow for this.""".trimMargin(),
                )
                RecursivePrompt(
                    listOf(
                        SimplePrompt("Amount Spent: ", inputReader, outPrinter) { it!!.toDouble() },
                        SimplePrompt(
                            "Select Category Account: ",
                            inputReader,
                            outPrinter,
                        ) { name: String? ->
                            budgetData.categoryAccounts.find { it.name == name }
                        },
                        SimplePrompt(
                            "Select Real Account: ",
                            inputReader,
                            outPrinter,
                        ) { name: String? ->
                            budgetData.realAccounts.find { it.name == name }
                        },
                    ),
                ) {
                    Transaction(it[0] as Double, it[1] as CategoryAccount, it[2] as RealAccount)
                }
                    .getResult()
                    .commit()
            },
        )
        add(
            takeAction("Write Checks or Use Credit Cards") {
                outPrinter(
                    """
            |Writing a check or using a credit card is slightly different from paying cash or using a debit card.
            |You will have a category fund account, called a drafts account, associated with each checking account and a category fund account associated with each credit card.
            |When a check is written, the amount is transferred from one category fund account to another:
            |the category account (such as food or rent) is debited and the draft or credit card account is credited with the same amount.
            |This is because you have not actually lost the money until you pay the credit card bill or until the check clears.""".trimMargin(),
                )
            },
        )
        add(
            takeAction("Clear Drafts") {
                outPrinter(
                    """
                |When the user gets a report from the bank telling which checks have cleared, the user needs to update the records in the budget program.
                |This is done by removing the transaction from the draft account and turning it into a debit in the associated checking account.
                |Optional: Do something similar for credit cards.
                |When the user receives a credit card bill, she should be able to check off those transactions that are covered on that bill.""".trimMargin(),
                )
            },
        )
        add(
            takeAction("Transfer Money") {
                outPrinter(
                    """
            |The user should be able to record transfers between read fund accounts
            |(e.g., a cash withdrawl is a transfer from savings to pocket) and transfers between category fund accounts
            |(e.g., when a big expenditure comes up under entertainment, you may need to transfer money from the school account.)""".trimMargin(),
                )
            },
        )
        add(
            takeActionAndPush("Customize", customizeMenu) {
                outPrinter(
                    """
            |The user must be able to add/remove accounts and categorize accounts (category fund account, real fund account, etc.)
            |The user may change the information associated with some account.
            |The user may associate a drafts account with a checking account and vice-versa.""".trimMargin(),
                )
            },
        )
        add(quitItem)
    }
