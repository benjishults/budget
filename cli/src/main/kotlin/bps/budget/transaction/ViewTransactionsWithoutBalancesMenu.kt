package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.persistence.AccountTransactionEntity
import bps.budget.persistence.TransactionDao
import bps.budget.ui.formatAsLocalDateTime
import bps.console.app.MenuSession
import bps.console.io.OutPrinter
import bps.console.menu.MenuItem
import bps.console.menu.ScrollingSelectionMenu
import kotlinx.datetime.TimeZone
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TRANSACTIONS_WITHOUT_BALANCES_TABLE_HEADER =
    "    Time Stamp          | Amount     | Description"

@Suppress("DefaultLocale")
@OptIn(ExperimentalUuidApi::class)
open class ViewTransactionsWithoutBalancesMenu<A : Account>(
    private val account: A,
    private val transactionDao: TransactionDao,
    private val budgetId: Uuid,
    private val accountIdToAccountMap: (Uuid) -> Account?,
    private val timeZone: TimeZone,
    limit: Int = 30,
    offset: Int = 0,
    private val filter: (AccountTransactionEntity) -> Boolean = { true },
    header: () -> String? = { "'${account.name}' Account Transactions" },
    prompt: () -> String,
    val outPrinter: OutPrinter,
    extraItems: List<MenuItem> = emptyList(),
    actOnSelectedItem: (MenuSession, AccountTransactionEntity) -> Unit,
    /* = { _, extendedTransactionItem: TransactionDao.ExtendedTransactionItem ->
        outPrinter.showTransactionDetailsAction(
            extendedTransactionItem.transaction(budgetId, accountIdToAccountMap),
            timeZone,
        )
    }*/
) : ScrollingSelectionMenu<AccountTransactionEntity>(
    header = {
        """
            |${header()}
            |$TRANSACTIONS_WITHOUT_BALANCES_TABLE_HEADER
        """.trimMargin()
    },
    prompt = prompt,
    limit = limit,
    offset = offset,
    extraItems = extraItems,
    labelGenerator = {
        String.format(
            "%s | %,10.2f | %s",
            timestamp
                .formatAsLocalDateTime(timeZone),
            amount,
            description,
        )
    },
    itemListGenerator = { selectedLimit: Int, selectedOffset: Int ->
        with(transactionDao) {
            fetchTransactionItemsInvolvingAccount(
                accountId = account.id,
                limit = selectedLimit,
                offset = selectedOffset,
                types = emptyList(),
                balanceAtStartOfPage = null,
                budgetId = account.budgetId,
            )
                .filter(filter)
                .sortedWith { o1: AccountTransactionEntity, o2: AccountTransactionEntity -> -o1.compareTo(o2) }
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
