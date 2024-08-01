@file:JvmName("Budget")

package bps.budget

import bps.budget.customize.customizeMenu
import bps.budget.data.BudgetData
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.budgetDaoBuilder
import bps.budget.transaction.Transaction
import bps.budget.ui.ConsoleUiFunctions
import bps.budget.ui.UiFunctions
import bps.console.MenuApplicationWithQuit
import bps.console.inputs.RecursivePrompt
import bps.console.inputs.SimplePrompt
import bps.console.inputs.TimestampPrompt
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.menuBuilder
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import bps.console.menu.takeActionAndPush
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun main(args: Array<String>) {
    val configurations = BudgetConfigurations(sequenceOf("budget.yml", "~/.config/bps-budget/budget.yml"))
    val uiFunctions = ConsoleUiFunctions()

    BudgetApplication(
        uiFunctions,
        configurations,
        uiFunctions.inputReader,
        uiFunctions.outPrinter,
    )
        .use() {
            it.run()
        }
}

class BudgetApplication private constructor(
    inputReader: InputReader,
    outPrinter: OutPrinter,
    configurations: BudgetConfigurations,
    uiFunctions: UiFunctions,
    val budgetDao: BudgetDao<*>,
) : AutoCloseable {

    constructor(
        uiFunctions: UiFunctions,
        configurations: BudgetConfigurations,
        inputReader: InputReader = DefaultInputReader,
        outPrinter: OutPrinter = DefaultOutPrinter,
    ) : this(
        inputReader,
        outPrinter,
        configurations,
        uiFunctions,
        budgetDaoBuilder(configurations.persistence),
    )

    val budgetData: BudgetData = BudgetData(uiFunctions, budgetDao)
    private val menuApplicationWithQuit =
        MenuApplicationWithQuit(AllMenus().budgetMenu(budgetData, budgetDao), inputReader, outPrinter)

    fun run() {
        menuApplicationWithQuit.run()
    }

    override fun close() {
        budgetDao.save(budgetData)
        budgetDao.close()
        menuApplicationWithQuit.close()
    }

}

const val spendMoneyItemLabel = "Record Transactions"

fun AllMenus.budgetMenu(budgetData: BudgetData, budgetDao: BudgetDao<*>): Menu =
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
                        SimplePrompt("Amount Spent: ", inputReader, outPrinter) { it!!.toCurrencyAmount() },
                        SimplePrompt("Description: ", inputReader, outPrinter),
                        TimestampPrompt(inputReader, outPrinter),
                        SimplePrompt(
                            // TODO need to show list
                            "Select Real Account: ",
                            inputReader,
                            outPrinter,
                        ) { name: String? ->
                            budgetData.realAccounts.find { it.name == name }
                        },
                        // TODO allow more than one
                        SimplePrompt(
                            // TODO need to show list
                            "Select Category Account: ",
                            inputReader,
                            outPrinter,
                        ) { name: String? ->
                            budgetData.categoryAccounts.find { it.name == name }
                        },
                    ),
                ) { inputs: List<*> ->
                    Transaction(
                        inputs[0] as BigDecimal,
                        inputs[1] as String,
                        OffsetDateTime
                            .of(inputs[2] as LocalDateTime, ZoneOffset.of(ZoneOffset.systemDefault().id)),
                        // TODO postgres stores at UTC.  Not sure if I need to set that or if it will translate automatically
//                            .atZoneSameInstant(ZoneId.of("UTC"))
//                            .toOffsetDateTime(),
//                        listOf(inputs[3] as )

                    )
                }
                    .getResult()
                    .also { budgetDao.commit(it) }
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

fun String.toCurrencyAmount(): BigDecimal =
    BigDecimal(this).setScale(2)
