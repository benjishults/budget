package bps.budget.transaction

import bps.budget.WithIo
import bps.budget.min
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.UserConfiguration
import bps.budget.toCurrencyAmountOrNull
import bps.console.inputs.SimplePromptWithDefault
import bps.console.io.DefaultOutPrinter
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.MenuItem
import bps.console.menu.MenuSession
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal

const val TRANSACTIONS_TABLE_HEADER = """
    Time Stamp          | Amount     | Description"""

open class ViewTransactionsMenu(
    private val account: Account,
    private val budgetDao: BudgetDao,
    private val budgetData: BudgetData,
    limit: Int = 30,
    offset: Int = 0,
    private val filter: (Transaction.Item) -> Boolean = { true },
    header: String? = "'${account.name}' Account Transactions",
    prompt: String = "Select transaction for details: ",
    val outPrinter: OutPrinter = DefaultOutPrinter,
    extraItems: List<MenuItem> = emptyList(),
    actOnSelectedItem: (MenuSession, Transaction.Item) -> Unit = { _, transactionItem: Transaction.Item ->
        outPrinter.showTransactionDetailsAction(transactionItem.transaction, budgetData)
    },
) : ScrollingSelectionMenu<Transaction.Item>(
    header + TRANSACTIONS_TABLE_HEADER,
    prompt,
    limit,
    offset,
    extraItems = extraItems,
    labelGenerator = {
        String.format(
            "%s | %,10.2f | %s",
            transaction
                .timestamp
                .toLocalDateTime(budgetData.timeZone)
                .format(
                    LocalDateTime.Format {
                        date(
                            LocalDate.Format {
                                year()
                                char('-')
                                monthNumber()
                                char('-')
                                dayOfMonth()
                            },
                        )
                        char(' ')
                        time(
                            LocalTime.Format {
                                hour()
                                char(':')
                                minute()
                                char(':')
                                second()
                            },
                        )
                    },
                ),
            amount,
            description ?: transaction.description,
        )
    },
    itemListGenerator = { selectedLimit: Int, selectedOffset: Int ->
        budgetDao
            .fetchTransactions(
                account = account,
                data = budgetData,
                limit = selectedLimit,
                offset = selectedOffset,
            )
            .flatMap { transaction: Transaction ->
                when (account) {
                    is CategoryAccount -> {
                        transaction.categoryItems
                    }
                    is ChargeAccount -> {
                        transaction.chargeItems
                    }
                    is RealAccount -> {
                        transaction.realItems
                    }
                    is DraftAccount -> {
                        transaction.draftItems
                    }
                }
                    .filter { item: Transaction.Item ->
                        when (account) {
                            is CategoryAccount -> {
                                item.categoryAccount
                            }
                            is ChargeAccount -> {
                                item.chargeAccount
                            }
                            is RealAccount -> {
                                item.realAccount
                            }
                            is DraftAccount -> {
                                item.draftAccount
                            }
                        } == account
                    }
                    .filter(filter)
            }
            .sorted()
    },
    actOnSelectedItem = actOnSelectedItem,
) {

    init {
        require(offset >= 0) { "Offset must not be negative: $offset" }
        require(limit > 0) { "Limit must be positive: $limit" }
    }
}

private fun OutPrinter.showTransactionDetailsAction(transaction: Transaction, budgetData: BudgetData) {
    invoke(
        buildString {
            append(
                transaction
                    .timestamp
                    .toLocalDateTime(budgetData.timeZone)
                    .format(
                        LocalDateTime.Format {
                            date(
                                LocalDate.Format {
                                    year()
                                    char('-')
                                    monthNumber()
                                    char('-')
                                    dayOfMonth()
                                },
                            )
                            char(' ')
                            time(
                                LocalTime.Format {
                                    hour()
                                    char(':')
                                    minute()
                                    char(':')
                                    second()
                                },
                            )
                        },
                    ),
            )
            append("\n")
            append(transaction.description)
            append("\n")
            appendItems("Category Account", transaction.categoryItems) { categoryAccount!! }
            appendItems("Real Items:", transaction.realItems) { realAccount!! }
            appendItems("Credit Card Items:", transaction.chargeItems) { chargeAccount!! }
            appendItems("Draft Items:", transaction.draftItems) { draftAccount!! }
        },
    )
}

private fun StringBuilder.appendItems(
    accountColumnLabel: String,
    items: List<Transaction.Item>,
    accountGetter: Transaction.Item.() -> Account,
) {
    if (items.isNotEmpty()) {
        append(String.format("%16s | Amount     | Description\n", accountColumnLabel))
        items
            .sorted()
            .forEach { transactionItem: Transaction.Item ->
                append(
                    String.format(
                        "%-16s | %10.2f |%s",
                        accountGetter(transactionItem).name,
                        transactionItem.amount,
                        transactionItem
                            .description
                            ?.let { " $it" }
                            ?: "",
                    ),
                )
                append("\n")
            }
    }
}

fun WithIo.viewHistoryMenu(
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
            addAll(budgetData.chargeAccounts)
        },
        // TODO https://github.com/benjishults/budget/issues/7
//        extraItems = listOf(item("View Inactive Accounts") {}),
        labelGenerator = {
            String.format("%,10.2f | %s | %s", balance, name, description)
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

fun WithIo.createTransactionItemMenu(
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
