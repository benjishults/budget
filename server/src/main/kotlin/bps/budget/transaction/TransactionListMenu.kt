package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.model.TransactionItem
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.TransactionDao
import bps.budget.ui.formatAsLocalDateTime
import bps.console.app.MenuSession
import bps.console.io.DefaultOutPrinter
import bps.console.io.OutPrinter
import bps.console.menu.MenuItem
import bps.console.menu.ScrollingSelectionWithContextMenu
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.max

private const val TRANSACTIONS_TABLE_HEADER = "    Time Stamp          | Amount     | Balance    | Description"

/**
 * The default behavior after selecting an item is to show details.  Pass a value for [actOnSelectedItem]
 * (and [prompt]) to override that behavior.
 */
open class TransactionListMenu<A : Account>(
    private val budgetData: BudgetData,
    private val account: A,
    private val transactionDao: TransactionDao,
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
    header: () -> String? = { "'${account.name}' Account Transactions" },
    prompt: () -> String = { "Select transaction for details: " },
    val outPrinter: OutPrinter = DefaultOutPrinter,
    extraItems: List<MenuItem> = emptyList(),
    actOnSelectedItem: (MenuSession, TransactionDao.ExtendedTransactionItem<A>) -> Unit =
        { _: MenuSession, extendedTransactionItem: TransactionDao.ExtendedTransactionItem<A> ->
            // NOTE this is needed so that when this menu is re-displayed, it will be where it started
            contextStack.removeLast()
            with(ViewTransactionFixture) {
                outPrinter.showTransactionDetailsAction(
                    extendedTransactionItem.transaction(budgetId, accountIdToAccountMap),
                    timeZone,
                )
            }
        },
) : ScrollingSelectionWithContextMenu<TransactionDao.ExtendedTransactionItem<A>, BigDecimal>(
    header = {
        """
            |${header()}
            |$TRANSACTIONS_TABLE_HEADER
        """.trimMargin()
    },
    prompt = prompt,
    limit = limit,
    offset = offset,
    contextStack = contextStack,
    extraItems = extraItems,
    labelGenerator = {
        String.format(
            "%s | %,10.2f | %,10.2f | %s",
            transactionTimestamp
                .formatAsLocalDateTime(timeZone),
            amount,
            accountBalanceAfterItem,
            description ?: transactionDescription,
        )
    },
    itemListGenerator = { selectedLimit: Int, selectedOffset: Int ->
        with(transactionDao) {
            fetchTransactionItemsInvolvingAccount(
                account = account,
                limit = selectedLimit,
                offset = selectedOffset,
                balanceAtEndOfPage = contextStack.lastOrNull() ?: account.balance,
            )
                .sortedWith { o1, o2 -> -o1.compareTo(o2) }
        }
    },
    actOnSelectedItem = actOnSelectedItem,
) {

    init {
        require(offset >= 0) { "Offset must not be negative: $offset" }
        require(limit > 0) { "Limit must be positive: $limit" }
    }

    /**
     * Add the current context to the stack immediately when the list is generated.
     */
    override fun List<TransactionDao.ExtendedTransactionItem<A>>.produceCurrentContext(): BigDecimal =
        lastOrNull()
            ?.run {
                accountBalanceAfterItem!! - amount
            }
            ?: account.balance

    override fun nextPageMenuProducer(): TransactionListMenu<A> =
        TransactionListMenu(
            account = account,
            transactionDao = transactionDao,
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
            actOnSelectedItem = actOnSelectedItem,
            budgetData = budgetData,
        )

    override fun previousPageMenuProducer(): TransactionListMenu<A> =
        TransactionListMenu(
            account = account,
            transactionDao = transactionDao,
            budgetId = budgetId,
            accountIdToAccountMap = accountIdToAccountMap,
            timeZone = timeZone,
            header = header,
            prompt = prompt,
            limit = limit,
            offset = max(offset - limit, 0),
            contextStack = contextStack
                .apply {
                    // NOTE this is why we need to override this method
                    removeLast()
                },
            extraItems = extraItems,
            outPrinter = outPrinter,
            actOnSelectedItem = actOnSelectedItem,
            budgetData = budgetData,
        )

}

object ViewTransactionFixture {

    fun OutPrinter.showTransactionDetailsAction(transaction: Transaction, timeZone: TimeZone) {
        invoke(
            buildString {
                append(
                    transaction
                        .timestamp
                        .formatAsLocalDateTime(timeZone),
                )
                append("\n")
                append(transaction.description)
                append("\n")
                appendItems("Category", transaction.categoryItems)
                appendItems("Real", transaction.realItems)
                appendItems("Credit Card", transaction.chargeItems)
                appendItems("Draft", transaction.draftItems)
            },
        )
    }

    fun StringBuilder.appendItems(
        accountColumnLabel: String,
        items: List<TransactionItem<*>>,
    ) {
        if (items.isNotEmpty()) {
            append(String.format("%-16s | Amount     | Description\n", accountColumnLabel))
            items
                .sortedWith { item1, item2 ->
                    when (item1) {
                        is Transaction.Item<*> -> item1.compareTo(item2 as Transaction.Item<*>)
                        is TransactionDao.ExtendedTransactionItem<*> -> item1.compareTo(item2 as TransactionDao.ExtendedTransactionItem<*>)
                        else -> throw IllegalArgumentException()
                    }
                }
                .forEach { transactionItem: TransactionItem<*> ->
                    append(
                        String.format(
                            "%-16s | %10.2f |%s",
                            transactionItem.account.name,
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
