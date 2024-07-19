@file:JvmName("Budget")

package bps.budget

import bps.budget.customize.customizeMenu
import bps.budget.data.BudgetData
import bps.budget.ui.ConsoleUiFunctions
import bps.console.MenuApplicationWithQuit
import bps.console.menu.Menu
import bps.console.menu.menuBuilder
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import bps.console.menu.takeActionAndPush

fun main(args: Array<String>) {
    val uiFunctions = ConsoleUiFunctions()
    val configurations = BudgetConfigurations(sequenceOf("budget.yml", "~/.config/bps-budget/budget.yml"))
    val budgetData = BudgetData(configurations.persistence, uiFunctions)
    BudgetApplication(uiFunctions, configurations, budgetData).run()
}

class BudgetApplication(
    val uiFunctions: ConsoleUiFunctions,
    val configurations: BudgetConfigurations,
    val budgetData: BudgetData,
) : MenuApplicationWithQuit(AllMenus().budgetMenu) {

    override fun run() {
        try {
            super.run()
        } finally {
            // TODO make this a hook in the MenuApplicationWithQuit
            uiFunctions.saveData(configurations.persistence, budgetData)
        }
    }

}

val AllMenus.budgetMenu: Menu
    get() =
        menuBuilder("Budget!") {
            add(
                takeAction("Record Income") {
                    outPrinter(
                        """
            |The user should enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money should be automatically entered into the general virtual fund account.""".trimMargin(),
                    )
                },
            )
            add(
                takeAction("Make Allowances") {
                    outPrinter(
                        """
                |Every month or so, the user may want to distribute the income from the general virtual fund accounts into the other virtual fund accounts.
                |Optional: You may want to add options to automate this procedure for the user.
                |I.e., let the user decide on a predetermined amount that will be transferred to each virtual fund account each month.
                |For some virtual fund accounts the user may prefer to bring the balance up to a certain amount each month.""".trimMargin(),
                    )
                },
            )
            add(
                takeAction("Record Transactions") {
                    outPrinter(
                        """
                |When the user has spent money, he needs to record the expenditure.
                |The user needs to enter the amount spent, the virtual fund account that it was taken from (e.g. food or rent) and the real fund account that the money came from (e.g., pocket or checking).
                |The same amount is debited from the real and the virtual accounts.
                |Optional but recommended:
                |Sometimes one expenditure from a real fund account will have several virtual funds account associated with it.
                |For example, if I write a check at WalMart, some of that may have been for necessities, some for food, some for school (books), and some for entertainment.
                |The user interface should allow for this.""".trimMargin(),
                    )
                },
            )
            add(
                takeAction("Write Checks or Use Credit Cards") {
                    outPrinter(
                        """
            |Writing a check or using a credit card is slightly different from paying cash or using a debit card.
            |You will have a virtual fund account, called a drafts account, associated with each checking account and a virtual fund account associated with each credit card.
            |When a check is written, the amount is transferred from one virtual fund account to another:
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
            |(e.g., a cash withdrawl is a transfer from savings to pocket) and transfers between virtual fund accounts
            |(e.g., when a big expenditure comes up under entertainment, you may need to transfer money from the school account.)""".trimMargin(),
                    )
                },
            )
            add(
                takeActionAndPush("Customize", customizeMenu) {
                    outPrinter(
                        """
            |The user must be able to add/remove accounts and categorize accounts (virtual fund account, real fund account, etc.)
            |The user may change the information associated with some account.
            |The user may associate a drafts account with a checking account and vice-versa.""".trimMargin(),
                    )
                },
            )
            add(quitItem)
        }
