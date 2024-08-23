@file:JvmName("Budget")

package bps.budget

import bps.budget.auth.User
import bps.budget.customize.customizeMenu
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
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
                .budgetMenu(budgetData, budgetDao, configurations.user, clock),
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
    userConfig: UserConfiguration,
    clock: Clock,
): Menu {
    return Menu("Budget!") {
        add(
            takeActionAndPush(
                recordIncome,
                { recordIncomeSelectionMenu(budgetData, budgetDao, userConfig, clock) },
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
                { makeAllowancesSelectionMenu(budgetData, budgetDao, userConfig, clock) },
            ) {
                outPrinter(
                    "Every month or so, you may want to distribute the income from the \"general\" category fund account into the other category fund accounts.\n",
                )
            },
        )
        add(
            pushMenu(
                recordSpending,
            ) { recordSpendingMenu(budgetData, budgetDao, userConfig, clock) },
        )
        add(
            pushMenu(
                viewHistory,
            ) { viewHistoryMenu(budgetData, budgetDao, userConfig) },
        )
        add(
            takeActionAndPush(
                recordDrafts,
                { recordDraftsMenu(budgetData, budgetDao, userConfig, clock) },
            ) {
                outPrinter(
                    """
            |Writing a check or using a credit card is slightly different from paying cash or using a debit card.
            |You will have a "drafts" account associated with each checking account or credit card.
            |When a check is written or credit card charged, the amount is transferred from the category accounts
            |(such as food or rent) to the "draft" account.
            |When the check clears or the credit card bill is paid, those transactions are cleared from the "draft" account.
            |""".trimMargin(),
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
            takeActionAndPush(setup, { customizeMenu }) {
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

private fun AllMenus.recordDraftsMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
) = ScrollingSelectionMenu(
    header = "Select account the draft or charge was made on",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.draftAccounts,
    labelGenerator = { String.format("%,10.2f | %s", realCompanion.balance - balance, name) },
) { menuSession, selectedAccount: DraftAccount ->
    // TODO enter check number if checking account
    val max = selectedAccount.realCompanion.balance - selectedAccount.balance
    val min = BigDecimal.ZERO.setScale(2)
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount of check or charge on ${selectedAccount.name} ($min, $max]: ",
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
    val description: String =
        SimplePrompt<String>(
            "Enter description of recipient of draft or charge: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
        )
            .getResult()
    val timestamp: Instant =
        TimestampPrompt("Use current time [Y]? ", budgetData.timeZone, clock, inputReader, outPrinter)
            .getResult()
            .toInstant(budgetData.timeZone)
    val transactionBuilder: Transaction.Builder =
        Transaction.Builder(description, timestamp)
            .apply {
                draftItemBuilders.add(
                    Transaction.ItemBuilder(
                        amount = amount,
                        description = description,
                        draftAccount = selectedAccount,
                    ),
                )
            }
    menuSession.push(
        createTransactionItemMenu(
            amount,
            transactionBuilder,
            description,
            budgetData,
            budgetDao,
            userConfig,
        ),
    )
}

private fun AllMenus.recordSpendingMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
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
            "Enter the amount spent from ${selectedRealAccount.name} ($min, $max]: ",
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
    val description: String =
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
    val transactionBuilder: Transaction.Builder =
        Transaction.Builder(description, timestamp)
            .apply {
                realItemBuilders.add(
                    Transaction.ItemBuilder(
                        amount = -amount,
                        description = description,
                        realAccount = selectedRealAccount,
                    ),
                )
            }
    menuSession.push(
        createTransactionItemMenu(
            amount,
            transactionBuilder,
            description,
            budgetData,
            budgetDao,
            userConfig,
        ),
    )
}

private fun AllMenus.createTransactionItemMenu(
    runningTotal: BigDecimal,
    transactionBuilder: Transaction.Builder,
    description: String,
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = "Select a category that some of that money was spent on.  Left to cover: \$$runningTotal",
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.categoryAccounts,
        labelGenerator = {
            String.format(
                "%,10.2f | %s",
                balance +
                        transactionBuilder
                            .categoryItemBuilders
                            .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                                if (this == itemBuilder.categoryAccount)
                                    runningValue + itemBuilder.amount!!
                                else
                                    runningValue
                            },
                name,
            )
        },
    ) { menuSession: MenuSession, selectedCategoryAccount: CategoryAccount ->
        val categoryAmount: BigDecimal =
            SimplePromptWithDefault<BigDecimal>(
                "Enter the amount spent on ${
                    selectedCategoryAccount.name
                } (0.00, [${
                    min(
                        runningTotal,
                        selectedCategoryAccount.balance +
                                transactionBuilder
                                    .categoryItemBuilders
                                    .fold(BigDecimal.ZERO.setScale(2)) { runningValue, itemBuilder ->
                                        if (selectedCategoryAccount == itemBuilder.categoryAccount)
                                            runningValue + itemBuilder.amount!!
                                        else
                                            runningValue
                                    },
                    )
                }]]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                defaultValue = runningTotal,
            ) {
                it.toCurrencyAmount() ?: BigDecimal.ZERO.setScale(2)
            }
                .getResult()
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
                amount = -categoryAmount,
                description = if (categoryDescription == description) null else categoryDescription,
                categoryAccount = selectedCategoryAccount,
            ),
        )
        menuSession.pop()
        if (runningTotal - categoryAmount > BigDecimal.ZERO) {
            menuSession.push(
                createTransactionItemMenu(
                    runningTotal - categoryAmount,
                    transactionBuilder,
                    description,
                    budgetData,
                    budgetDao,
                    userConfig,
                ),
            )
        } else {
            val transaction = transactionBuilder.build()
            budgetData.commit(transaction)
            budgetDao.commit(transaction, budgetData.id)
        }
    }

private fun AllMenus.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
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
    userConfig: UserConfiguration,
    clock: Clock,
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
            "Enter the amount to allocate into ${selectedCategoryAccount.name} ($min, $max]: ",
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

fun min(a: BigDecimal, b: BigDecimal): BigDecimal =
    if ((a <= b)) a else b
