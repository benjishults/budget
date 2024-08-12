package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.jdbc.toLocalDateTime
import bps.console.io.DefaultOutPrinter
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.MenuItem
import bps.console.menu.MenuSession
import bps.console.menu.backItem
import bps.console.menu.item
import bps.console.menu.quitItem

open class ViewTransactionsMenu(
    transactions: List<Transaction>,
    private val account: Account,
    private val budgetDao: BudgetDao,
    private val budgetData: BudgetData,
    private val limit: Int = 30,
    val offset: Long = 0L,
    override val header: String? = "$account Transactions",
    override val prompt: String = "Select transaction for details: ",
    val outPrinter: OutPrinter = DefaultOutPrinter,
) : Menu {

    init {
        require(offset >= 0) { "Offset must not be negative: $offset" }
        require(limit > 0) { "Limit must be positive: $limit" }
    }

    override val items: List<MenuItem> =
        (transactions.map { item ->
            item("${item.timestamp.toLocalDateTime(budgetData.timeZone)} | ${item.description}") {
                showTransactionDetailsAction(item)
            }
        } +
                previousTransactionsMenuItem())
            .let { menuItems: List<MenuItem> ->
                if (offset != 0L)
                    listOf(nextTransactionsMenuItem())
                else
                    menuItems
            } +
                backItem +
                quitItem

    private fun previousTransactionsMenuItem(): MenuItem =
        item("Previous Transactions") { menuSession: MenuSession ->
            menuSession.popOrNull()
            menuSession.push(
                ViewTransactionsMenu(
                    transactions =
                    budgetDao.fetchTransactions(
                        account = account,
                        data = budgetData,
                        limit = limit,
                        offset = offset + limit,
                    ),
                    account = account,
                    budgetDao = budgetDao,
                    budgetData = budgetData,
                    limit = limit,
                    offset = offset + limit,
                ),
            )
        }

    private fun nextTransactionsMenuItem(): MenuItem =
        item("Next Transactions") { menuSession: MenuSession ->
            menuSession.popOrNull()
            menuSession.push(
                ViewTransactionsMenu(
                    transactions =
                    budgetDao.fetchTransactions(
                        account = account,
                        data = budgetData,
                        limit = limit,
                        offset = offset - limit,
                    ),
                    account = account,
                    budgetDao = budgetDao,
                    budgetData = budgetData,
                    limit = limit,
                    offset = offset - limit,
                ),
            )
        }

    private fun showTransactionDetailsAction(item: Transaction) {
        outPrinter(
            buildString {
                append(item.timestamp.toLocalDateTime(budgetData.timeZone))
                append("\n")
                append(item.description)
                append("\n")
                appendItems("Category Items:", item.categoryItems) { categoryAccount!! }
                appendItems("Real Items:", item.realItems) { realAccount!! }
                appendItems("Draft Items:", item.draftItems) { draftAccount!! }
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
}
