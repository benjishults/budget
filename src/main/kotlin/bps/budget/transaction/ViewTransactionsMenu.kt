package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.ui.format
import bps.console.app.MenuSession
import bps.console.io.DefaultOutPrinter
import bps.console.io.OutPrinter
import bps.console.menu.MenuItem
import bps.console.menu.ScrollingSelectionMenu
import bps.console.menu.ScrollingSelectionWithContextMenu
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.max

const val TRANSACTIONS_TABLE_HEADER = "    Time Stamp          | Amount     | Balance    | Description"

open class ViewTransactionsMenu(
    private val account: Account,
    private val budgetDao: BudgetDao,
    private val budgetId: UUID,
    private val accountIdToAccountMap: Map<UUID, Account>,
    private val timeZone: TimeZone,
    limit: Int = 30,
    offset: Int = 0,
    /**
     * If empty, we assume that the account balance is the balance at the end.
     * In the [produceCurrentContext] method, we add the balance prior to the page to [contextStack].
     */
    contextStack: MutableList<BigDecimal> = mutableListOf(),
    private val filter: (BudgetDao.ExtendedTransactionItem) -> Boolean = { true },
    header: String? = "'${account.name}' Account Transactions",
    prompt: String = "Select transaction for details: ",
    val outPrinter: OutPrinter = DefaultOutPrinter,
    extraItems: List<MenuItem> = emptyList(),
    actOnSelectedItem: (MenuSession, BudgetDao.ExtendedTransactionItem) -> Unit = { _, extendedTransactionItem: BudgetDao.ExtendedTransactionItem ->
        // NOTE this is needed so that when this menu is re-displayed, it will be where it started
        contextStack.removeLast()
        outPrinter.showTransactionDetailsAction(
            extendedTransactionItem.transaction(budgetId, accountIdToAccountMap),
            timeZone,
        )
    },
) : ScrollingSelectionWithContextMenu<BudgetDao.ExtendedTransactionItem, BigDecimal>(
    """
        |$header
        |$TRANSACTIONS_TABLE_HEADER
    """.trimMargin(),
    prompt,
    limit,
    offset,
    contextStack,
    extraItems = extraItems,
    labelGenerator = {
        String.format(
            "%s | %,10.2f | %,10.2f | %s",
            transactionTimestamp
                .format(timeZone),
            item.amount,
            accountBalanceAfterItem,
            item.description ?: transactionDescription,
        )
    },
    itemListGenerator = { selectedLimit: Int, selectedOffset: Int ->
        with(budgetDao) {
            fetchTransactionItemsInvolvingAccount(
                account = account,
                limit = selectedLimit,
                offset = selectedOffset,
                balanceAtEndOfPage = contextStack.lastOrNull() ?: account.balance,
            )
                .filter(filter)
                .sortedWith { o1, o2 -> -o1.compareTo(o2) }
        }
    },
    actOnSelectedItem = actOnSelectedItem,
) {

    init {
        require(offset >= 0) { "Offset must not be negative: $offset" }
        require(limit > 0) { "Limit must be positive: $limit" }
    }

    fun priorBalance(): BigDecimal = contextStack.last()

    /**
     * Add the current context to the stack immediately when the list is generated.
     */
    override fun List<BudgetDao.ExtendedTransactionItem>.produceCurrentContext(): BigDecimal =
        lastOrNull()
            ?.run {
                accountBalanceAfterItem - item.amount
            }
            ?: account.balance

    override fun nextPageMenuProducer(): ScrollingSelectionMenu<BudgetDao.ExtendedTransactionItem> =
        ViewTransactionsMenu(
            account = account,
            budgetDao = budgetDao,
            budgetId = budgetId,
            accountIdToAccountMap = accountIdToAccountMap,
            timeZone = timeZone,
            header = header,
            prompt = prompt,
            limit = limit,
            offset = offset + limit,
            contextStack = contextStack,
            extraItems = extraItems,
            outPrinter = outPrinter,
            filter = filter,
            actOnSelectedItem = actOnSelectedItem,
        )

    override fun previousPageMenuProducer(): ViewTransactionsMenu =
        ViewTransactionsMenu(
            account = account,
            budgetDao = budgetDao,
            budgetId = budgetId,
            accountIdToAccountMap = accountIdToAccountMap,
            timeZone = timeZone,
            header = header,
            prompt = prompt,
            limit = limit,
            offset = max(offset - limit, 0),
            contextStack = contextStack
                .apply { removeLast() },
            extraItems = extraItems,
            outPrinter = outPrinter,
            filter = filter,
            actOnSelectedItem = actOnSelectedItem,
        )

    companion object {

        private fun OutPrinter.showTransactionDetailsAction(transaction: Transaction, timeZone: TimeZone) {
            invoke(
                buildString {
                    append(
                        transaction
                            .timestamp
                            .format(timeZone),
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
                                transactionItem.accountGetter().name,
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

    }

}
