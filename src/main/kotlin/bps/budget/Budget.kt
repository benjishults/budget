@file:JvmName("Budget")

package bps.budget

import bps.budget.customize.customizeMenu
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.budgetDaoBuilder
import bps.budget.persistence.budgetDataFactory
import bps.budget.transaction.ViewTransactionsMenu
import bps.budget.ui.ConsoleUiFacade
import bps.budget.ui.UiFacade
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.TimestampPrompt
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.MenuApplicationWithQuit
import bps.console.menu.MenuSession
import bps.console.menu.SelectionMenu
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import bps.console.menu.takeActionAndPush
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import java.math.BigDecimal

fun main(args: Array<String>) {
    val configurations = BudgetConfigurations(sequenceOf("budget.yml", "~/.config/bps-budget/budget.yml"))
    val uiFunctions = ConsoleUiFacade()

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
    uiFacade: UiFacade,
    val budgetDao: BudgetDao,
    val clock: Clock,
) : AutoCloseable {

    constructor(
        uiFacade: UiFacade,
        configurations: BudgetConfigurations,
        inputReader: InputReader = DefaultInputReader,
        outPrinter: OutPrinter = DefaultOutPrinter,
        clock: Clock = Clock.System,
    ) : this(
        inputReader,
        outPrinter,
        uiFacade,
        budgetDaoBuilder(configurations.persistence),
        clock,
    )

    val budgetData: BudgetData = budgetDataFactory(uiFacade, budgetDao)
    private val menuApplicationWithQuit =
        MenuApplicationWithQuit(
            AllMenus(inputReader, outPrinter)
                .budgetMenu(budgetData, budgetDao, clock),
            inputReader,
            outPrinter,
        )

    fun run() {
        menuApplicationWithQuit.run()
    }

    override fun close() {
        budgetDao.save(budgetData)
        budgetDao.close()
        menuApplicationWithQuit.close()
    }

}

fun AllMenus.budgetMenu(budgetData: BudgetData, budgetDao: BudgetDao, clock: Clock): Menu {
    return Menu("Budget!") {
        add(
            takeActionAndPush(
                recordIncome,
                recordIncomeSelectionMenu(budgetData, budgetDao, clock),
            ) {
                outPrinter(
                    """
            |Enter the real fund account into which the money is going (e.g., savings).
            |The same amount of money will be automatically entered into the '${budgetData.generalAccount.name}' account.
            |""".trimMargin(),
                )
            },
        )
        add(
            takeActionAndPush(
                makeAllowances,
                makeAllowancesSelectionMenu(budgetData, budgetDao, clock),
            ) {
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
            takeActionAndPush(
                recordSpending,
                SelectionMenu(
                    header = "Select real account money was spent from.",
                    itemListGenerator = { budgetData.realAccounts },
                    labelSelector = { String.format("%,10.2f | %s", balance, name) },
                ) { menuSession: MenuSession, selectedRealAccount: RealAccount ->
                    val max = selectedRealAccount.balance
                    val min = BigDecimal.ZERO.setScale(2)
                    val amount: BigDecimal =
                        SimplePrompt<BigDecimal>(
                            "Enter the amount spent from ${selectedRealAccount.name} ($min - $max]: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            validate = { input: String ->
                                input
                                    .toCurrencyAmount()
                                    ?.let {
                                        min < it && it <= max
                                    }
                                    ?: false
                            },
                        ) {
                            it.toCurrencyAmount() ?: BigDecimal.ZERO.setScale(2)
                        }
                            .getResult()
                    val description =
                        SimplePromptWithDefault(
                            "Enter description of transaction [spending]: ",
                            defaultValue = "spending",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                        )
                            .getResult()
                    val timestamp: Instant =
                        TimestampPrompt("Use current time [Y]? ", budgetData.timeZone, clock, inputReader, outPrinter)
                            .getResult()
                            .toInstant(budgetData.timeZone)
                    val transactionBuilder: Transaction.Builder = Transaction.Builder(description, timestamp)
                    transactionBuilder.realItemBuilders.add(
                        Transaction.ItemBuilder(
                            amount = amount,
                            description = description,
                            realAccount = selectedRealAccount,
                        ),
                    )
                    var runningTotal = amount
                    while (runningTotal > BigDecimal.ZERO) {
                        menuSession.push(
                            SelectionMenu(
                                header = "Select a category that some of that money was spent on.  Left to cover: $runningTotal",
                                itemListGenerator = { budgetData.categoryAccounts },
                                labelSelector = { String.format("%,10.2f | %s", balance, name) },
                            ) { _: MenuSession, selectedCategoryAccount: CategoryAccount ->
                                val categoryAmount: BigDecimal =
                                    SimplePrompt<BigDecimal>(
                                        "Enter the amount spent on ${selectedCategoryAccount.name} ($min - $runningTotal]: ",
                                        inputReader = inputReader,
                                        outPrinter = outPrinter,
                                        validate = { input: String ->
                                            input
                                                .toCurrencyAmount()
                                                ?.let {
                                                    min < it && it <= runningTotal
                                                }
                                                ?: false
                                        },
                                    ) {
                                        it.toCurrencyAmount() ?: BigDecimal.ZERO.setScale(2)
                                    }
                                        .getResult()
                                runningTotal -= categoryAmount
                                val categoryDescription =
                                    SimplePromptWithDefault(
                                        "Enter description for ${selectedCategoryAccount.name} spend [$description]: ",
                                        defaultValue = description,
                                        inputReader = inputReader,
                                        outPrinter = outPrinter,
                                    )
                                        .getResult()
                                transactionBuilder.categoryItemBuilders.add(
                                    Transaction.ItemBuilder(
                                        amount = categoryAmount,
                                        description = if (categoryDescription == description) null else categoryDescription,
                                        categoryAccount = selectedCategoryAccount,
                                    ),
                                )

                            },
                        )
                    }
                    menuSession.popOrNull()
                    val transaction = transactionBuilder.build()
                    budgetData.commit(transaction)
                    budgetDao.commit(transaction)
                },
            ) {
                outPrinter(
                    """
                |Optional but recommended:
                |Sometimes one expenditure from a real fund account will have several category funds account associated with it.
                |For example, if I write a check at WalMart, some of that may have been for necessities, some for food, some for school (books), and some for entertainment.
                |The user interface should allow for this.""".trimMargin(),
                )
            },
        )
//                RecursivePrompt(
//                    listOf(
//                        SimplePrompt("Amount Spent: ", inputReader, outPrinter) { it!!.toCurrencyAmount() },
//                        SimplePrompt("Description: ", inputReader, outPrinter),
//                        TimestampPrompt("Time: ", inputReader, outPrinter),
//                        SimplePrompt(
//                            // TODO need to show list
//                            "Select Real Account: ",
//                            inputReader,
//                            outPrinter,
//                        ) { name: String? ->
//                            budgetData.getAccountById<>()realAccounts.find { it.name == name }
//                        },
//                        // TODO allow more than one
//                        SimplePrompt(
//                            // TODO need to show list
//                            "Select Category Account: ",
//                            inputReader,
//                            outPrinter,
//                        ) { name: String? ->
//                            budgetData.categoryAccounts.find { it.name == name }
//                        },
//                    ),
//                ) { inputs: List<*> ->
//                    Transaction(
//                        inputs[0] as BigDecimal,
//                        inputs[1] as String,
//                        OffsetDateTime
//                            .of(inputs[2] as LocalDateTime, ZoneOffset.of(ZoneOffset.systemDefault().id)),
//                        // TODO postgres stores at UTC.  Not sure if I need to set that or if it will translate automatically
////                            .atZoneSameInstant(ZoneId.of("UTC"))
////                            .toOffsetDateTime(),
////                        listOf(inputs[3] as )
//
//                    )
//                }
//                    .getResult()
//                    .also { budgetDao.commit(it) }
//            },
//        )
        add(
            pushMenu(
                viewHistory,
                // TODO make this scrolling before things get out of hand
                SelectionMenu(
                    header = "Select account to view history",
                    itemListGenerator = {
                        buildList {
                            add(budgetData.generalAccount)
                            addAll(budgetData.categoryAccounts - budgetData.generalAccount)
                            addAll(budgetData.realAccounts)
                            addAll(budgetData.draftAccounts)
                        }
                    },
                    labelSelector = {
                        String.format("%,10.2f | %s", balance, name)
                    },
                ) { menuSession: MenuSession, selectedAccount: Account ->
                    menuSession.push(
                        ViewTransactionsMenu(
                            account = selectedAccount,
                            budgetDao = budgetDao,
                            budgetData = budgetData,
                            outPrinter = outPrinter,
                        ),
                    )
                },
            ),
        )
        add(
            takeAction(recordDrafts) {
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
            takeAction(clearDrafts) {
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
            takeAction(transfer) {
                outPrinter(
                    """
            |The user should be able to record transfers between read fund accounts
            |(e.g., a cash withdrawl is a transfer from savings to pocket) and transfers between category fund accounts
            |(e.g., when a big expenditure comes up under entertainment, you may need to transfer money from the school account.)""".trimMargin(),
                )
            },
        )
        add(
            takeActionAndPush(setup, customizeMenu) {
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
}

private fun AllMenus.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    clock: Clock,
): Menu = SelectionMenu(
    header = "Select account receiving the income:",
    itemListGenerator = { budgetData.realAccounts },
    labelSelector = { String.format("%,10.2f | %s", balance, name) },
) { menuSession: MenuSession, realAccount: RealAccount ->
    val amount =
        SimplePrompt(
            "Enter the amount of income: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
        ) {
            it.toCurrencyAmount() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
    val description =
        SimplePromptWithDefault(
            "Enter description of income [income]: ",
            defaultValue = "income",
            inputReader = inputReader,
            outPrinter = outPrinter,
        )
            .getResult()
    val timestamp: Instant =
        TimestampPrompt("Use current time [Y]? ", budgetData.timeZone, clock, inputReader, outPrinter)
            .getResult()
            .toInstant(budgetData.timeZone)
    val income: Transaction = Transaction.Builder(description, timestamp)
        .apply {
            categoryItemBuilders.add(
                Transaction.ItemBuilder(
                    amount,
                    categoryAccount = budgetData.generalAccount,
                ),
            )
            realItemBuilders.add(Transaction.ItemBuilder(amount, realAccount = realAccount))
        }
        .build()
    budgetData.commit(income)
    budgetDao.commit(income)
}

private fun AllMenus.makeAllowancesSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    clock: Clock,
): Menu = SelectionMenu(
    header = "Select account to allocate money into from ${budgetData.generalAccount.name}: ",
    itemListGenerator = { budgetData.categoryAccounts - budgetData.generalAccount },
    labelSelector = { String.format("%,10.2f | %s", balance, name) },
) { menuSession: MenuSession, selectedCategoryAccount: CategoryAccount ->
    val max = budgetData.generalAccount.balance
    val min = BigDecimal.ZERO.setScale(2)
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount to allocate into ${selectedCategoryAccount.name} ($min - $max]: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = { input: String ->
                input
                    .toCurrencyAmount()
                    ?.let {
                        min < it && it <= max
                    }
                    ?: false
            },
        ) {
            it.toCurrencyAmount() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
    val description =
        SimplePromptWithDefault(
            "Enter description of transaction [allowance]: ",
            defaultValue = "allowance",
            inputReader = inputReader,
            outPrinter = outPrinter,
        )
            .getResult()
    val allocate = Transaction.Builder(description, clock.now())
        .apply {
            categoryItemBuilders.add(
                Transaction.ItemBuilder(
                    -amount,
                    categoryAccount = budgetData.generalAccount,
                ),
            )
            categoryItemBuilders.add(
                Transaction.ItemBuilder(
                    amount,
                    categoryAccount = selectedCategoryAccount,
                ),
            )
        }
        .build()
    budgetData.commit(allocate)
    budgetDao.commit(allocate)
}

fun String.toCurrencyAmount(): BigDecimal? =
    try {
        BigDecimal(this).setScale(2)
    } catch (e: NumberFormatException) {
        null
    }
