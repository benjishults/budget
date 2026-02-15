@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.transaction

import bps.budget.model.Account
import bps.budget.model.AccountData
import bps.budget.model.BudgetData
import bps.budget.persistence.AccountTransactionEntity
import bps.budget.persistence.AllocatedItemEntities
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.TransactionEntity
import bps.budget.persistence.TransactionItemEntity
import bps.budget.persistence.allocateItemsByAccountType
import bps.budget.ui.formatAsLocalDateTime
import bps.console.app.MenuSession
import bps.console.io.DefaultOutPrinter
import bps.console.io.OutPrinter
import bps.console.menu.MenuItem
import bps.console.menu.ScrollingSelectionWithContextMenu
import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TRANSACTIONS_TABLE_HEADER = "    Time Stamp          | Amount     | Balance    | Description"

/**
 * The default behavior after selecting an item is to show details.  Pass a value for [actOnSelectedItem]
 * (and [prompt]) to override that behavior.
 */
@Suppress("DefaultLocale")
@OptIn(ExperimentalUuidApi::class)
open class TransactionListMenu<A : Account>(
    private val budgetData: BudgetData,
    private val account: A,
    private val transactionDao: TransactionDao,
    private val budgetId: Uuid,
    private val accountIdToAccountMap: (Uuid) -> Account?,
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
    actOnSelectedItem: (MenuSession, AccountTransactionEntity) -> Unit =
        { _: MenuSession, extendedTransactionItem: AccountTransactionEntity ->
            // NOTE this is needed so that when this menu is re-displayed, it will be where it started
            contextStack.removeLast()
            with(ViewTransactionFixture) {
                outPrinter.showTransactionDetailsAction(
                    transactionDao.getTransactionOrNull(
                        extendedTransactionItem.transactionId,
                        extendedTransactionItem.budgetId,
                    )!!,
                    timeZone,
                    accountIdToAccountMap,
                )
            }
        },
) : ScrollingSelectionWithContextMenu<AccountTransactionEntity, BigDecimal>(
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
            timestamp
                .formatAsLocalDateTime(timeZone),
            amount,
            balance,
            description ?: transactionDao.getTransactionOrNull(transactionId, budgetId)!!.description,
        )
    },
    itemListGenerator = { selectedLimit: Int, selectedOffset: Int ->
        with(transactionDao) {
            fetchTransactionItemsInvolvingAccount(
                accountId = account.id,
                limit = selectedLimit,
                offset = selectedOffset,
                balanceAtStartOfPage = contextStack.lastOrNull() ?: account.balance,
                types = emptyList(),
                budgetId = budgetId,
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
    override fun List<AccountTransactionEntity>.produceCurrentContext(): BigDecimal =
        lastOrNull()
            ?.run {
                balance!! - amount
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

    fun OutPrinter.showTransactionDetailsAction(
        transaction: TransactionEntity,
        timeZone: TimeZone,
        accountIdToAccountMap: (Uuid) -> AccountData?,
    ) {
        invoke(
            buildString {
                val allocatedItems: AllocatedItemEntities =
                    transaction.allocateItemsByAccountType(accountIdToAccountMap)
                append(
                    transaction
                        .timestamp
                        .formatAsLocalDateTime(timeZone),
                )
                append("\n")
                append(transaction.description)
                append("\n")
                appendItems("Category", allocatedItems.category, accountIdToAccountMap)
                appendItems("Real", allocatedItems.real, accountIdToAccountMap)
                appendItems("Credit Card", allocatedItems.charge, accountIdToAccountMap)
                appendItems("Draft", allocatedItems.draft, accountIdToAccountMap)
            },
        )
    }

    @Suppress("DefaultLocale")
    fun StringBuilder.appendItems(
        accountColumnLabel: String,
        items: List<TransactionItemEntity>,
        accountIdToAccountMap: (Uuid) -> AccountData?,
    ) {
        if (items.isNotEmpty()) {
            append(String.format("%-16s | Amount     | Description\n", accountColumnLabel))
            items
                // NOTE just need a consistent sort for tests
                .sortedWith { item1: TransactionItemEntity, item2: TransactionItemEntity ->
                    accountIdToAccountMap(item1.accountId)!!
                        .name
                        .compareTo(
                            accountIdToAccountMap(item2.accountId)!!
                                .name,
                        )
//                    when (item1) {
//                        is Transaction.Item<*> -> item1.compareTo(item2 as Transaction.Item<*>)
//                        is TransactionDao.ExtendedTransactionItem<*> -> item1.compareTo(item2 as TransactionDao.ExtendedTransactionItem<*>)
//                        else -> throw IllegalArgumentException()
//                    }
                }
                .forEach { transactionItem: TransactionItemEntity ->
                    append(
                        String.format(
                            "%-16s | %10.2f |%s",
                            accountIdToAccountMap(transactionItem.accountId)!!.name,
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
