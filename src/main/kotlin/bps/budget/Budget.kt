@file:JvmName("Budget")

package bps.budget

import bps.budget.auth.User
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.DraftStatus
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
import bps.config.convertToPath
import bps.console.inputs.SimplePrompt
import bps.console.inputs.SimplePromptWithDefault
import bps.console.inputs.getTimestampFromUser
import bps.console.io.DefaultInputReader
import bps.console.io.DefaultOutPrinter
import bps.console.io.InputReader
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.MenuApplicationWithQuit
import bps.console.menu.MenuSession
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.backItem
import bps.console.menu.pushMenu
import bps.console.menu.quitItem
import bps.console.menu.takeAction
import bps.console.menu.takeActionAndPush
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal
import kotlin.math.min

fun main() {
    val configurations =
        BudgetConfigurations(sequenceOf("budget.yml", convertToPath("~/.config/bps-budget/budget.yml")))
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
            WithIo(inputReader, outPrinter)
                .budgetMenu(budgetData, budgetDao, configurations.user, user, clock),
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

fun WithIo.budgetMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    user: User,
    clock: Clock,
): Menu =
    Menu("Budget!") {
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
            pushMenu(
                writeOrClearChecks,
            ) { checksMenu(budgetData, budgetDao, userConfig, clock) },
        )
        add(
            pushMenu(useOrPayCreditCards) {
                creditCardMenu(budgetData, budgetDao, userConfig, clock)
            },
        )
        add(
            takeAction(transfer) {
                outPrinter(
                    """
            |The user should be able to record transfers between read fund accounts
            |(e.g., a cash withdrawal is a transfer from savings to pocket) and transfers between category fund accounts
            |(e.g., when a big expenditure comes up under entertainment, you may need to transfer money from the school account.)""".trimMargin(),
                )
            },
        )
        add(
            pushMenu(setup, { customizeMenu(budgetData, budgetDao, user, userConfig, clock) }),
        )
        add(quitItem)
    }

fun BudgetData.deleteCategoryAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(userConfig, deleter = { deleteCategoryAccount(it) }) { categoryAccounts - generalAccount }

fun BudgetData.deleteRealAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(userConfig, deleter = { deleteRealAccount(it) }) { realAccounts }

fun BudgetData.deleteChargeAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(userConfig, deleter = { deleteChargeAccount(it) }) { chargeAccounts }

fun BudgetData.deleteDraftAccountMenu(userConfig: UserConfiguration): Menu =
    deleteAccountMenu(userConfig, deleter = { deleteDraftAccount(it) }) { draftAccounts }

fun <T : Account> deleteAccountMenu(
    userConfig: UserConfiguration,
    deleter: (T) -> Unit,
    deleteFrom: () -> List<T>,
): Menu =
    ScrollingSelectionMenu(
        header = "Select account to delete",
        limit = userConfig.numberOfItemsInScrollingList,
        itemListGenerator = { limit, offset ->
            val baseList = deleteFrom()
            baseList.subList(
                offset,
                min(baseList.size, offset + limit),
            )
        },
        labelGenerator = { String.format("%,10.2f | %s", balance, name) },
    ) { _: MenuSession, selectedAccount: T ->
        deleter(selectedAccount)
    }

private fun WithIo.customizeMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    user: User,
    userConfig: UserConfiguration,
    clock: Clock,
) =
    Menu {
        add(
            takeAction("Create a New Category") {
                val name: String =
                    SimplePrompt<String>(
                        "Enter a unique name for the new category: ",
                        inputReader,
                        outPrinter,
                        validate = { input ->
                            budgetData
                                .categoryAccounts
                                .none { account ->
                                    account.name == input
                                }
                        },
                    )
                        .getResult()
                        .trim()
                if (name.isNotBlank()) {
                    val description: String =
                        SimplePromptWithDefault(
                            "Enter a description for the new category: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            .trim()
                    budgetData.addCategoryAccount(CategoryAccount(name, description))
                    budgetDao.save(budgetData, user)
                }
            },
        )
        add(
            pushMenu("Delete an Account") {
                Menu("What kind af account do you want to delete?") {
                    add(
                        pushMenu("Category Account") {
                            budgetData.deleteCategoryAccountMenu(userConfig)
                        },
                    )
                    add(
                        pushMenu("Real Account") {
                            budgetData.deleteRealAccountMenu(userConfig)
                        },
                    )
                    add(
                        pushMenu("Charge Account") {
                            budgetData.deleteChargeAccountMenu(userConfig)
                        },
                    )
                    add(
                        pushMenu("Draft Account") {
                            budgetData.deleteDraftAccountMenu(userConfig)
                        },
                    )
                    add(backItem)
                    add(quitItem)
                }
            },
        )
        add(
            takeAction("Create a Real Fund") {
                val name: String =
                    SimplePrompt<String>(
                        "Enter a unique name for the real account: ",
                        inputReader,
                        outPrinter,
                        validate = { input ->
                            budgetData
                                .realAccounts
                                .none { account ->
                                    account.name == input
                                }
                        },
                    )
                        .getResult()
                        .trim()
                if (name.isNotBlank()) {
                    val accountDescription: String =
                        SimplePromptWithDefault(
                            "Enter a description for the real account: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            .trim()
                    val isDraft: Boolean = SimplePromptWithDefault(
                        "Will you write checks on this account [y/N]? ",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                        defaultValue = false,
                    ) { it.trim() in listOf("Y", "y", "true", "yes") }
                        .getResult()
                    val balance: BigDecimal = SimplePromptWithDefault(
                        basicPrompt = "Initial balance on account [0.00]:  (This amount will be added to your General account as well.) ",
                        defaultValue = BigDecimal.ZERO.setScale(2),
                        additionalValidation = { !it.trim().startsWith("-") },
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                    ) {
                        it.toCurrencyAmountOrNull()
                    }
                        .getResult()
                        ?: BigDecimal.ZERO.setScale(2)
                    val realAccount = RealAccount(name, accountDescription)
                    budgetData.addRealAccount(realAccount)
                    if (balance >= BigDecimal.ZERO.setScale(2)) {
                        val incomeDescription: String =
                            SimplePromptWithDefault(
                                "Enter description of income [initial balance in '${realAccount.name}']: ",
                                defaultValue = "initial balance in '${realAccount.name}'",
                                inputReader = inputReader,
                                outPrinter = outPrinter,
                            )
                                .getResult()
                        outPrinter("Enter timestamp for '$incomeDescription' transaction\n")
                        val timestamp: Instant = getTimestampFromUser(
                            timeZone = budgetData.timeZone,
                            clock = clock,
                        )
                        budgetData.commit(
                            createIncomeTransaction(
                                incomeDescription,
                                timestamp,
                                balance,
                                budgetData,
                                realAccount,
                            ),
                        )
                    }
                    if (isDraft)
                        budgetData.addDraftAccount(DraftAccount(name, accountDescription, realCompanion = realAccount))
                    budgetDao.save(budgetData, user)
                }
            },
        )
        add(
            takeAction("Add a Credit Card") {
                val name: String =
                    SimplePrompt<String>(
                        "Enter a unique name for the new credit card: ",
                        inputReader,
                        outPrinter,
                        validate = { input ->
                            budgetData
                                .chargeAccounts
                                .none { account ->
                                    account.name == input
                                }
                        },
                    )
                        .getResult()
                        .trim()
                if (name.isNotBlank()) {
                    val description: String =
                        SimplePromptWithDefault(
                            "Enter a description for the new credit card: ",
                            inputReader = inputReader,
                            outPrinter = outPrinter,
                            defaultValue = name,
                        )
                            .getResult()
                            .trim()
                    budgetData.addChargeAccount(ChargeAccount(name, description))
                    budgetDao.save(budgetData, user)
                }
            },
        )
        add(backItem)
        add(quitItem)
    }

private fun WithIo.viewHistoryMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
): Menu =
    ScrollingSelectionMenu(
        header = "Select account to view history",
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = buildList {
            add(budgetData.generalAccount)
            addAll(budgetData.categoryAccounts - budgetData.generalAccount)
            addAll(budgetData.realAccounts)
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

private fun WithIo.checksMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu =
    ScrollingSelectionMenu(
        header = "Select the checking account",
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.draftAccounts,
        labelGenerator = { String.format("%,10.2f | %s", realCompanion.balance - balance, name) },
    ) { menuSession, draftAccount: DraftAccount ->
        menuSession.push(
            Menu {
                add(
                    takeAction("Write a check on ${draftAccount.name}") {
                        // TODO enter check number if checking account
                        // NOTE this is why we have separate draft accounts -- to easily know the real vs draft balance
                        val max = draftAccount.realCompanion.balance - draftAccount.balance
                        val min = BigDecimal.ZERO.setScale(2)
                        val amount: BigDecimal =
                            SimplePromptWithDefault<BigDecimal>(
                                "Enter the amount of check on ${draftAccount.name} [$min, $max]: ",
                                inputReader = inputReader,
                                outPrinter = outPrinter,
                                defaultValue = min,
                                additionalValidation = { input: String ->
                                    input
                                        .toCurrencyAmountOrNull()
                                        ?.let {
                                            it in min..max
                                        }
                                        ?: false
                                },
                            ) {
                                it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
                            }
                                .getResult()
                        if (amount > BigDecimal.ZERO) {
                            val description: String =
                                SimplePrompt<String>(
                                    "Enter the recipient of the check: ",
                                    inputReader = inputReader,
                                    outPrinter = outPrinter,
                                )
                                    .getResult()
                            val timestamp: Instant =
                                getTimestampFromUser(
                                    timeZone = budgetData.timeZone,
                                    clock = clock,
                                )
                            val transactionBuilder: Transaction.Builder =
                                Transaction.Builder(description, timestamp)
                                    .apply {
                                        draftItemBuilders.add(
                                            Transaction.ItemBuilder(
                                                amount = amount,
                                                description = description,
                                                draftAccount = draftAccount,
                                                draftStatus = DraftStatus.outstanding,
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
                    },
                )
                add(
                    pushMenu("Record check cleared") {
                        ViewTransactionsMenu(
                            filter = { it.draftStatus === DraftStatus.outstanding },
                            header = "Select the check that cleared",
                            prompt = "Select the check that cleared: ",
                            account = draftAccount,
                            budgetDao = budgetDao,
                            budgetData = budgetData,
                            limit = userConfig.numberOfItemsInScrollingList,
                            outPrinter = outPrinter,
                        ) { _, draftTransactionItem ->
                            val timestamp: Instant =
                                getTimestampFromUser(
                                    "Did the check clear just now [Y]? ",
                                    budgetData.timeZone,
                                    clock,
                                )
                            val clearingTransaction =
                                Transaction.Builder(
                                    draftTransactionItem.transaction.description,
                                    timestamp,
                                )
                                    .apply {
                                        draftItemBuilders.add(
                                            Transaction.ItemBuilder(
                                                amount = -draftTransactionItem.amount,
                                                description = description,
                                                draftAccount = draftAccount,
                                                draftStatus = DraftStatus.clearing,
                                            ),
                                        )
                                        realItemBuilders.add(
                                            Transaction.ItemBuilder(
                                                amount = -draftTransactionItem.amount,
                                                description = description,
                                                realAccount = draftTransactionItem.draftAccount!!.realCompanion,
                                            ),
                                        )
                                    }
                                    .build()
                            budgetData.commit(clearingTransaction)
                            budgetDao.clearCheck(
                                draftTransactionItem,
                                clearingTransaction,
                                budgetData.id,
                            )
                        }
                    },
                )
                add(backItem)
                add(quitItem)
            },
        )
    }

private fun WithIo.creditCardMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu =
    ScrollingSelectionMenu(
        header = "Select a credit card",
        limit = userConfig.numberOfItemsInScrollingList,
        baseList = budgetData.chargeAccounts,
        // TODO do we want to incorporate credit limits to determine the balance and max
        labelGenerator = { String.format("%,10.2f | %s", balance, name) },
    ) { menuSession, chargeAccount: ChargeAccount ->
        menuSession.push(
            Menu {
                add(
                    takeAction("Record spending on ${chargeAccount.name}") {
                        spendOnACreditCard(
                            budgetData,
                            clock,
                            budgetDao,
                            userConfig,
                            menuSession,
                            chargeAccount,
                        )
                    },
                )
                add(
                    takeAction("Pay ${chargeAccount.name} bill") {
                        payCreditCardBill(
                            menuSession,
                            userConfig,
                            budgetData,
                            clock,
                            chargeAccount,
                            budgetDao,
                        )
                    },
                )
                add(
                    pushMenu("View unpaid transactions on ${chargeAccount.name}") {
                        ViewTransactionsMenu(
                            account = chargeAccount,
                            budgetDao = budgetDao,
                            budgetData = budgetData,
                            limit = userConfig.numberOfItemsInScrollingList,
                            filter = { it.draftStatus === DraftStatus.outstanding },
                            header = "Unpaid transactions on ${chargeAccount.name}",
                            prompt = "Select transaction to view details: ",
                            outPrinter = outPrinter,
                            extraItems = listOf(), // TODO toggle cleared/outstanding
                        )
                    },
                )
                add(backItem)
                add(quitItem)
            },
        )
    }

private fun WithIo.payCreditCardBill(
    menuSession: MenuSession,
    userConfig: UserConfiguration,
    budgetData: BudgetData,
    clock: Clock,
    chargeAccount: ChargeAccount,
    budgetDao: BudgetDao,
) {
    val amountOfBill: BigDecimal =
        SimplePrompt(
            basicPrompt = "Enter the total amount of the bill: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = {
                it
                    .toCurrencyAmountOrNull()
                    ?.let { amount ->
                        amount >= BigDecimal.ZERO
                    }
                    ?: false
            },
        ) { it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO }
            .getResult()
    if (amountOfBill > BigDecimal.ZERO) {
        menuSession.push(
            ScrollingSelectionMenu(
                header = "Select real account bill was paid from",
                limit = userConfig.numberOfItemsInScrollingList,
                baseList = budgetData.realAccounts,
                labelGenerator = { String.format("%,10.2f | %s", balance, name) },
            ) { _: MenuSession, selectedRealAccount: RealAccount ->
                val timestamp: Instant =
                    getTimestampFromUser(
                        "Use current time for the bill-pay transaction [Y]? ",
                        budgetData.timeZone,
                        clock,
                    )
                val description: String =
                    SimplePromptWithDefault(
                        "Description of transaction [${chargeAccount.name}]: ",
                        inputReader = inputReader,
                        outPrinter = outPrinter,
                        defaultValue = chargeAccount.name,
                    )
                        .getResult()
                val billPayTransaction: Transaction =
                    Transaction.Builder(description, timestamp)
                        .apply {
                            realItemBuilders.add(
                                Transaction.ItemBuilder(
                                    amount = -amountOfBill,
                                    description = description,
                                    realAccount = selectedRealAccount,
                                ),
                            )
                            chargeItemBuilders.add(
                                Transaction.ItemBuilder(
                                    amount = amountOfBill,
                                    description = description,
                                    chargeAccount = chargeAccount,
                                    draftStatus = DraftStatus.clearing,
                                ),
                            )
                        }
                        .build()
                //
                menuSession.push(
                    selectOrCreateChargeTransactionsForBill(
                        amountOfBill = amountOfBill,
                        billPayTransaction = billPayTransaction,
                        chargeAccount = chargeAccount,
                        selectedItems = emptyList(),
                        budgetData = budgetData,
                        budgetDao = budgetDao,
                        userConfig = userConfig,
                        menuSession = menuSession,
                        clock = clock,
                    ),
                )
            },
        )

    }
}

// TODO would be nice to display the already-selected transaction items as well
// TODO some folks might like to be able to pay an amount that isn't related to transactions on the card
private fun WithIo.selectOrCreateChargeTransactionsForBill(
    amountOfBill: BigDecimal,
    billPayTransaction: Transaction,
    chargeAccount: ChargeAccount,
    selectedItems: List<Transaction.Item>,
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    menuSession: MenuSession,
    clock: Clock,
): Menu = ViewTransactionsMenu(
    filter = { it.draftStatus === DraftStatus.outstanding && it !in selectedItems },
    header = "Select all transactions from this bill.  Amount to be covered: $${
        amountOfBill +
                selectedItems.fold(BigDecimal.ZERO) { sum, item ->
                    sum + item.amount
                }
    }",
    prompt = "Select a transaction covered in this bill: ",
    extraItems = listOf(
        takeAction("Record a missing transaction from this bill") {
            spendOnACreditCard(
                budgetData,
                clock,
                budgetDao,
                userConfig,
                menuSession,
                chargeAccount,
            )
        },
    ),
    account = chargeAccount,
    budgetDao = budgetDao,
    budgetData = budgetData,
    limit = userConfig.numberOfItemsInScrollingList,
    outPrinter = outPrinter,
) { _, chargeTransactionItem ->
    val allSelectedItems: List<Transaction.Item> = selectedItems + chargeTransactionItem
    // FIXME if the selected amount is greater than allowed, then give a "denied" message
    //       ... or don't show such items in the first place
    val remainingToBeCovered: BigDecimal =
        amountOfBill +
                allSelectedItems
                    .fold(BigDecimal.ZERO.setScale(2)) { sum, item ->
                        sum + item.amount
                    }
    when {
        remainingToBeCovered == BigDecimal.ZERO.setScale(2) -> {
            menuSession.pop()
            outPrinter("Payment recorded!\n")
            budgetData.commit(billPayTransaction)
            budgetDao.commitCreditCardPayment(
                allSelectedItems,
                billPayTransaction,
                budgetData.id,
            )
        }
        remainingToBeCovered < BigDecimal.ZERO -> {
            outPrinter("ERROR: this bill payment amount is not large enough to cover that transaction\n")
        }
        else -> {
            menuSession.pop()
            menuSession.push(
                selectOrCreateChargeTransactionsForBill(
                    amountOfBill = amountOfBill,
                    billPayTransaction = billPayTransaction,
                    chargeAccount = chargeAccount,
                    selectedItems = allSelectedItems,
                    budgetData = budgetData,
                    budgetDao = budgetDao,
                    userConfig = userConfig,
                    menuSession = menuSession,
                    clock = clock,
                ),
            )
        }
    }
}

private fun WithIo.spendOnACreditCard(
    budgetData: BudgetData,
    clock: Clock,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    menuSession: MenuSession,
    chargeAccount: ChargeAccount,
) {
    // TODO enter check number if checking account
    // NOTE this is why we have separate draft accounts -- to easily know the real vs draft balance
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount of the charge on ${chargeAccount.name}: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = { input: String ->
                input
                    .toCurrencyAmountOrNull()
                    ?.let {
                        it >= BigDecimal.ZERO
                    }
                    ?: false
            },
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
    if (amount > BigDecimal.ZERO) {
        val description: String =
            SimplePrompt<String>(
                "Enter the recipient of the charge: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
        val timestamp: Instant =
            getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
        val transactionBuilder: Transaction.Builder =
            Transaction.Builder(description, timestamp)
                .apply {
                    chargeItemBuilders.add(
                        Transaction.ItemBuilder(
                            amount = -amount,
                            description = description,
                            chargeAccount = chargeAccount,
                            draftStatus = DraftStatus.outstanding,
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
}

private fun WithIo.recordSpendingMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = "Select real account money was spent from.",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts,
    labelGenerator = { String.format("%,10.2f | %s", balance, name) },
) { menuSession: MenuSession, selectedRealAccount: RealAccount ->
    val max = selectedRealAccount.balance
    val min = BigDecimal.ZERO.setScale(2)
    val amount: BigDecimal =
        SimplePrompt<BigDecimal>(
            "Enter the amount spent from ${selectedRealAccount.name} [$min, $max]: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = { input: String ->
                input
                    .toCurrencyAmountOrNull()
                    ?.let {
                        it in min..max
                    }
                    ?: false
            },
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
    if (amount > BigDecimal.ZERO) {
        val description: String =
            SimplePromptWithDefault(
                "Enter description of transaction [spending]: ",
                defaultValue = "spending",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
        val timestamp: Instant =
            getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
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
}

private fun WithIo.createTransactionItemMenu(
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
        baseList = budgetData.categoryAccounts - budgetData.generalAccount,
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
        val max = min(
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
        val categoryAmount: BigDecimal =
            SimplePromptWithDefault<BigDecimal>(
                "Enter the amount spent on ${
                    selectedCategoryAccount.name
                } [0.00, [$max]]: ",
                inputReader = inputReader,
                outPrinter = outPrinter,
                defaultValue = max,
            ) {
                (it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2))
                    .let { entry: BigDecimal ->
                        if (entry < BigDecimal.ZERO || entry > max)
                            BigDecimal.ZERO.setScale(2)
                        else
                            entry
                    }
            }
                .getResult()
        if (categoryAmount > BigDecimal.ZERO) {
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
    }

fun WithIo.recordIncomeSelectionMenu(
    budgetData: BudgetData,
    budgetDao: BudgetDao,
    userConfig: UserConfiguration,
    clock: Clock,
): Menu = ScrollingSelectionMenu(
    header = "Select account receiving the income:",
    limit = userConfig.numberOfItemsInScrollingList,
    baseList = budgetData.realAccounts,
    labelGenerator = { String.format("%,10.2f | %s", balance, name) },
) { _: MenuSession, realAccount: RealAccount ->
    val amount: BigDecimal =
        SimplePrompt(
            "Enter the amount of income: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
            ?: BigDecimal.ZERO.setScale(2)
    if (amount <= BigDecimal.ZERO.setScale(2)) {
        outPrinter("\nNot recording non-positive income.\n\n")
    } else {
        val description: String =
            SimplePromptWithDefault(
                "Enter description of income [income into ${realAccount.name}]: ",
                defaultValue = "income into ${realAccount.name}",
                inputReader = inputReader,
                outPrinter = outPrinter,
            )
                .getResult()
        val timestamp: Instant = getTimestampFromUser(timeZone = budgetData.timeZone, clock = clock)
        val income: Transaction = createIncomeTransaction(description, timestamp, amount, budgetData, realAccount)
        budgetData.commit(income)
        budgetDao.commit(income, budgetData.id)
    }
}

fun createIncomeTransaction(
    description: String,
    timestamp: Instant,
    amount: BigDecimal,
    budgetData: BudgetData,
    realAccount: RealAccount,
) = Transaction.Builder(description, timestamp)
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

private fun WithIo.makeAllowancesSelectionMenu(
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
            "Enter the amount to allocate into ${selectedCategoryAccount.name} [$min, $max]: ",
            inputReader = inputReader,
            outPrinter = outPrinter,
            validate = { input: String ->
                input
                    .toCurrencyAmountOrNull()
                    ?.let {
                        it in min..max
                    }
                    ?: false
            },
        ) {
            it.toCurrencyAmountOrNull() ?: BigDecimal.ZERO.setScale(2)
        }
            .getResult()
    if (amount > BigDecimal.ZERO) {
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
}

fun String.toCurrencyAmountOrNull(): BigDecimal? =
    try {
        BigDecimal(this).setScale(2)
    } catch (e: NumberFormatException) {
        null
    }

fun min(a: BigDecimal, b: BigDecimal): BigDecimal =
    if ((a <= b)) a else b
