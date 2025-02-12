package bps.budget.persistence

import bps.budget.model.Account
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.Transaction
import bps.budget.model.Transaction.Type
import bps.budget.model.TransactionItem
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.util.UUID

interface TransactionDao {

    fun commit(
        transaction: Transaction,
        budgetId: UUID,
        saveBalances: Boolean = true,
    ) {
    }

    /**
     * This implementation throws a [NotImplementedError].
     * @return the list of [BalanceToAdd]s that should be applied to correct balances on accounts.
     */
    fun deleteTransaction(
        transactionId: UUID,
        budgetId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): List<AccountDao.BalanceToAdd> = TODO()

    fun clearCheck(
        draftTransactionItems: List<Transaction.Item<DraftAccount>>,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) = Unit

    fun clearCheck(
        draftTransactionItem: Transaction.Item<DraftAccount>,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) =
        clearCheck(listOf(draftTransactionItem), clearingTransaction, budgetId)

    fun commitCreditCardPayment(
        clearedItems: List<ExtendedTransactionItem<ChargeAccount>>,
        billPayTransaction: Transaction,
        budgetId: UUID,
    ) {
    }

    /**
     * @param balanceAtEndOfPage must be provided unless [offset] is `0`.
     * If not provided, then the balance from the account will be used.
     * Its value should be the balance of the account at the point when this page of results ended.
     */
    fun <A : Account> fetchTransactionItemsInvolvingAccount(
        account: A,
        limit: Int = 30,
        offset: Int = 0,
        balanceAtEndOfPage: BigDecimal? =
            require(offset == 0) { "balanceAtEndOfPage must be provided unless offset is 0." }
                .let { account.balance },
    ): List<ExtendedTransactionItem<A>> =
        emptyList()

    fun getTransactionOrNull(
        transactionId: UUID,
        budgetId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): Transaction?

    /**
     * See src/test/kotlin/bps/kotlin/GenericFunctionTest.kt for a discussion of how I want to improve this
     */
    class ExtendedTransactionItem<out A : Account>(
        val item: Transaction.ItemBuilder<A>,
        val accountBalanceAfterItem: BigDecimal?,
        val transactionId: UUID,
        val transactionDescription: String,
        val transactionTimestamp: Instant,
        val transactionType: Type,
        val transactionDao: TransactionDao,
        val budgetId: UUID,
    ) : TransactionItem<A>,
        Comparable<ExtendedTransactionItem<*>> {

        /**
         * The first time this is referenced, a call will be made to the DB to fetch the entire transaction.
         * So, refer to this only if you need more than just the [transactionId], [transactionDescription], or
         * [transactionTimestamp].
         */
        fun transaction(budgetId: UUID, accountIdToAccountMap: Map<UUID, Account>): Transaction =
            transaction
                ?: run {
                    transactionDao.getTransactionOrNull(transactionId, budgetId, accountIdToAccountMap)!!
                        .also {
                            transaction = it
                        }
                }

        // TODO be sure the protect this if we go multithreaded
        private var transaction: Transaction? = null

        override fun compareTo(other: ExtendedTransactionItem<*>): Int =
            this.transactionTimestamp.compareTo(other.transactionTimestamp)
                .let {
                    when (it) {
                        0 -> this.transactionId.compareTo(other.transactionId)
                        else -> it
                    }
                }

        override val amount: BigDecimal = item.amount
        override val description: String? = item.description
        override val account: A = item.account
        override val timestamp: Instant = transactionTimestamp

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtendedTransactionItem<*>) return false

            if (item != other.item) return false

            return true
        }

        override fun hashCode(): Int {
            return item.hashCode()
        }

        override fun toString(): String {
            return "ExtendedTransactionItem(transactionId=$transactionId, item=$item, accountBalanceAfterItem=$accountBalanceAfterItem)"
        }
    }
}
