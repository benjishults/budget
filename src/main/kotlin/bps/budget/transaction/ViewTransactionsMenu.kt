package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.TransactionDao
import bps.budget.ui.formatAsLocalDateTime
import bps.console.app.MenuSession
import bps.console.io.DefaultOutPrinter
import bps.console.io.OutPrinter
import bps.console.menu.Menu
import bps.console.menu.MenuItem
import bps.console.menu.ScrollingSelectionWithContextMenu
import bps.console.menu.item
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.max

private const val TRANSACTIONS_TABLE_HEADER = "    Time Stamp          | Amount     | Balance    | Description"

/**
 * The default behavior after selecting an item is to show details.  Pass a value for [actOnSelectedItem] to override
 * that behavior.
 */
open class ManageTransactionsMenu<A : Account>(
    private val budgetData: BudgetData,
    private val account: A,
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
            item.amount,
            accountBalanceAfterItem,
            item.description ?: transactionDescription,
        )
    },
    itemListGenerator = { selectedLimit: Int, selectedOffset: Int ->
        with(budgetDao.transactionDao) {
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
                accountBalanceAfterItem!! - item.amount
            }
            ?: account.balance

    override fun nextPageMenuProducer(): ManageTransactionsMenu<A> =
        ManageTransactionsMenu(
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
            actOnSelectedItem = actOnSelectedItem,
            budgetData = budgetData,
        )

    override fun previousPageMenuProducer(): ManageTransactionsMenu<A> =
        ManageTransactionsMenu(
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
                appendItems("Category Account", transaction.categoryItems)
                appendItems("Real Items:", transaction.realItems)
                appendItems("Credit Card Items:", transaction.chargeItems)
                appendItems("Draft Items:", transaction.draftItems)
            },
        )
    }

    fun StringBuilder.appendItems(
        accountColumnLabel: String,
        items: List<Transaction.Item<*>>,
    ) {
        if (items.isNotEmpty()) {
            append(String.format("%16s | Amount     | Description\n", accountColumnLabel))
            items
                .sorted()
                .forEach { transactionItem: Transaction.Item<*> ->
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
