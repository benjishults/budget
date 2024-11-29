package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.TransactionDao
import bps.budget.ui.formatAsLocalDateTime
import bps.console.app.MenuSession
import bps.console.io.OutPrinter
import bps.console.menu.MenuItem
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.TimeZone
import java.util.UUID
import kotlin.math.max

private const val TRANSACTIONS_WITHOUT_BALANCES_TABLE_HEADER =
    "    Time Stamp          | Amount     | Description"

open class ViewTransactionsWithoutBalancesMenu<A : Account>(
    private val account: A,
    private val transactionDao: TransactionDao,
    private val budgetId: UUID,
    private val accountIdToAccountMap: Map<UUID, Account>,
    private val timeZone: TimeZone,
    limit: Int = 30,
    offset: Int = 0,
    private val filter: (TransactionDao.ExtendedTransactionItem<A>) -> Boolean = { true },
    header: () -> String? = { "'${account.name}' Account Transactions" },
    prompt: () -> String,
    val outPrinter: OutPrinter,
    extraItems: List<MenuItem> = emptyList(),
    actOnSelectedItem: (MenuSession, TransactionDao.ExtendedTransactionItem<A>) -> Unit,
    /* = { _, extendedTransactionItem: TransactionDao.ExtendedTransactionItem ->
        outPrinter.showTransactionDetailsAction(
            extendedTransactionItem.transaction(budgetId, accountIdToAccountMap),
            timeZone,
        )
    }*/
) : ScrollingSelectionMenu<TransactionDao.ExtendedTransactionItem<A>>(
    {
        """
        |${header()}
        |$TRANSACTIONS_WITHOUT_BALANCES_TABLE_HEADER
    """.trimMargin()
    },
    prompt,
    limit,
    offset,
    extraItems = extraItems,
    labelGenerator = {
        String.format(
            "%s | %,10.2f | %s",
            transactionTimestamp
                .formatAsLocalDateTime(timeZone),
            amount,
            description ?: transactionDescription,
        )
    },
    itemListGenerator = { selectedLimit: Int, selectedOffset: Int ->
        with(transactionDao) {
            fetchTransactionItemsInvolvingAccount(
                account = account,
                limit = selectedLimit,
                offset = selectedOffset,
                balanceAtEndOfPage = null,
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

    override fun nextPageMenuProducer(): ViewTransactionsWithoutBalancesMenu<A> =
        ViewTransactionsWithoutBalancesMenu(
            account = account,
            transactionDao = transactionDao,
            budgetId = budgetId,
            accountIdToAccountMap = accountIdToAccountMap,
            timeZone = timeZone,
            header = header,
            prompt = prompt,
            limit = limit,
            offset = offset + limit,
            extraItems = extraItems,
            outPrinter = outPrinter,
            filter = filter,
            actOnSelectedItem = actOnSelectedItem,
        )

    override fun previousPageMenuProducer(): ViewTransactionsWithoutBalancesMenu<A> =
        ViewTransactionsWithoutBalancesMenu(
            account = account,
            transactionDao = transactionDao,
            budgetId = budgetId,
            accountIdToAccountMap = accountIdToAccountMap,
            timeZone = timeZone,
            header = header,
            prompt = prompt,
            limit = limit,
            offset = max(offset - limit, 0),
            extraItems = extraItems,
            outPrinter = outPrinter,
            filter = filter,
            actOnSelectedItem = actOnSelectedItem,
        )

}
