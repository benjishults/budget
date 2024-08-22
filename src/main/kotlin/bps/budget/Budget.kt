@file:JvmName("Budget")

package bps.budget

import bps.budget.auth.User
import bps.budget.customize.customizeMenu
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.persistence.buildBudgetDao
import bps.budget.persistence.getBudgetNameFromPersistenceConfig
import bps.budget.persistence.loadOrBuildBudgetData
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
import bps.console.menu.ScrollingSelectionMenu
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
    clock: Clock,
    configurations: BudgetConfigurations,
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
        buildBudgetDao(configurations.persistence),
        clock,
        configurations,
    )

    init {
        budgetDao.prepForFirstLoad()
    }

    val user: User = uiFacade.login(budgetDao, configurations.user)
    val budgetData: BudgetData = loadOrBuildBudgetData(
        user = user,
        uiFacade = uiFacade,
        budgetDao = budgetDao,
        budgetName = getBudgetNameFromPersistenceConfig(configurations.persistence) ?: uiFacade.getBudgetName(),
    )

    private val menuApplicationWithQuit =
        MenuApplicationWithQuit(
            AllMenus(inputReader, outPrinter)
                .budgetMenu(budgetData, budgetDao, clock, configurations.user),
            inputReader,
            outPrinter,
        )

    fun run() {
        menuApplicationWithQuit.run()
    }

    override fun close() {
        budgetDao.save(budgetData, user)
        budgetDao.close()
        menuApplicationWithQuit.close()
    }

}

fun AllMenus.budgetMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    clock: Clock,
    userConfig: UserConfiguration,
): Menu {
    return Menu("Budget!") {
        add(
            takeActionAndPush(
                recordIncome,
                recordIncomeSelectionMenu(budgetData, budgetDao, clock, userConfig),
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
                makeAllowancesSelectionMenu(budgetData, budgetDao, clock, userConfig),
            ) {
                outPrinter(
                    "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                )
            },
        )
        add(
            pushMenu(
                recordSpending,
                recordSpendingMenu(budgetData, clock, budgetDao, userConfig),
            ),
        )
        add(
            pushMenu(
                viewHistory,
                viewHistoryMenu(budgetData, budgetDao, userConfig),
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

private fun AllMenus.viewHistoryMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
) = ScrollingSelectionMenu(
    header = "Select account to view history",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = buildList {
        add(budgetData.generalAccount)
        addAll(budgetData.categoryAccounts - budgetData.generalAccount)
        addAll(budgetData.realAccounts)
        addAll(budgetData.draftAccounts)
    },
    labelGenerator = {
        String.format("%,10.2f | %s", balance, name)
    },
) { menuSession: MenuSession, selectedAccount: Account ->
    menuSession.push(
        ViewTransactionsMenu(
            account = selectedAccount,
            limit = userConfig.numberOfItemsInScrollingList,
            budgetDao = budgetDao,
            budgetData = budgetData,
            outPrinter = outPrinter,
        ),
    )
}

private fun AllMenus.recordSpendingMenu(
    budgetData: BudgetData,
    clock: Clock,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
) = ScrollingSelectionMenu(
    header = "Select real account money was spent from.",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts,
    labelGenerator = { String.format("%,10.2f | %s", balance, name) },
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
            ScrollingSelectionMenu(
                header = "Select a category that some of that money was spent on.  Left to cover: $runningTotal",
                limit = userConfig.numberOfItemsInScrollingList,
                baseList = budgetData.categoryAccounts,
                labelGenerator = { String.format("%,10.2f | %s", balance, name) },
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
    menuSession.pop()
    val transaction = transactionBuilder.build()
    budgetData.commit(transaction)
    budgetDao.commit(transaction, budgetData.id)
}

private fun AllMenus.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    clock: Clock,
    userConfig: UserConfiguration,
): Menu = ScrollingSelectionMenu(
    header = "Select account receiving the income:",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts,
    labelGenerator = { String.format("%,10.2f | %s", balance, name) },
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
            "Enter description of income [income into ${realAccount.name}]: ",
            defaultValue = "income into ${realAccount.name}",
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
    budgetDao.commit(income, budgetData.id)
}

private fun AllMenus.makeAllowancesSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    clock: Clock,
    userConfig: UserConfiguration,
): Menu = ScrollingSelectionMenu(
    header = "Select account to allocate money into from ${budgetData.generalAccount.name}: ",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.categoryAccounts - budgetData.generalAccount,
    labelGenerator = { String.format("%,10.2f | %s", balance, name) },
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
            "Enter description of transaction [allowance into ${selectedCategoryAccount.name}]: ",
            defaultValue = "allowance into ${selectedCategoryAccount.name}",
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
    budgetDao.commit(allocate, budgetData.id)
}

fun String.toCurrencyAmount(): BigDecimal? =
    try {
        BigDecimal(this).setScale(2)
    } catch (e: NumberFormatException) {
        null
    }
