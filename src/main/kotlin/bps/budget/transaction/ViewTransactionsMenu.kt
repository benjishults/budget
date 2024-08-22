package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.console.io.DefaultOutPrinter
import bps.console.io.OutPrinter
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.toLocalDateTime

open class ViewTransactionsMenu(
    private val account: Account,
    private val budgetDao: BudgetDao,
    private val budgetData: BudgetData,
    limit: Int = 30,
    offset: Int = 0,
    header: String? = """
        |$account Transactions
        |    Time Stamp          | Balance    | Description""".trimMargin(),
    prompt: String = "Select transaction for details: ",
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : ScrollingSelectionMenu<Pair<Transaction, Transaction.Item>>(
    header,
    prompt,
    limit,
    offset,
    labelGenerator = {
        val (transaction: Transaction, item: Transaction.Item) = this
        String.format(
            "%s | %,10.2f | %s",
            transaction
                .timestamp
                .toLocalDateTime(budgetData.timeZone)
                .toString()
                .replace('T', ' ')
                .let { timestampString ->

                    timestampString.lastIndexOf('.')
                        .takeIf { it != -1 }
                        ?.let { index ->
                            timestampString.substring(0, index)
                        }
                        ?: timestampString
                },
            item.amount,
            item.description ?: transaction.description,
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
                            is RealAccount -> {
                                item.realAccount
                            }
                            is DraftAccount -> {
                                item.draftAccount
                            }
                        } == account
                    }
                    .map { transaction to it }
            }
    },
    next = { _, (transaction: Transaction, _) ->
        outPrinter.showTransactionDetailsAction(transaction, budgetData)
    },
) {

    init {
        require(offset >= 0) { "Offset must not be negative: $offset" }
        require(limit > 0) { "Limit must be positive: $limit" }
    }
}

private fun OutPrinter.showTransactionDetailsAction(transaction: Transaction, budgetData: BudgetData) {
    invoke(
        buildString {
            append(transaction.timestamp.toLocalDateTime(budgetData.timeZone))
            append("\n")
            append(transaction.description)
            append("\n")
            appendItems("Category Items:", transaction.categoryItems) { categoryAccount!! }
            appendItems("Real Items:", transaction.realItems) { realAccount!! }
            appendItems("Draft Items:", transaction.draftItems) { draftAccount!! }
        },
    )
}

private fun StringBuilder.appendItems(
    label: String,
    items: List<Transaction.Item>,
    accountGetter: Transaction.Item.() -> Account,
) {
    if (items.isNotEmpty()) {
        append(label)
        append("\n")
        items.forEach { transactionItem: Transaction.Item ->
            append(transactionItem.accountGetter())
            append("\n")
            if (transactionItem.description !== null) {
                append(transactionItem.description)
                append("\n")
            }
            append(transactionItem.amount)
            append("\n")
            append("\n")
        }
    }
}
