package bps.budget.persistence

import bps.budget.auth.User
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.Transaction
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.util.UUID

/**
 * @param C the [BudgetConfig] type determining how the data is found in the first place.
 */
interface BudgetDao : AutoCloseable {

    fun getUserByLogin(login: String): User? = null

    fun createUser(login: String, password: String): UUID =
        UUID.randomUUID()

    fun prepForFirstLoad() {}

    /**
     * @throws DataConfigurationException if data isn't found.
     */
    fun load(budgetId: UUID, userId: UUID): BudgetData = TODO()

    fun prepForFirstSave() {}

    /**
     * Save top-level account data.  Persist adding and deleting accounts
     */
    fun save(data: BudgetData, user: User) {}

    fun commit(
        transaction: Transaction,
        budgetId: UUID,
        saveBalances: Boolean = true,
    ) {
    }

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

    fun deleteAccount(account: Account) {}
    fun deleteBudget(budgetId: UUID) {}
    fun deleteUser(userId: UUID) {}
    fun deleteUserByLogin(login: String) {}

    class ExtendedTransactionItem<out A : Account>(
        val item: Transaction.ItemBuilder<A>,
        val accountBalanceAfterItem: BigDecimal?,
        val transactionId: UUID,
        val transactionDescription: String,
        val transactionTimestamp: Instant,
        val budgetDao: BudgetDao,
        val budgetId: UUID,
    ) : Comparable<ExtendedTransactionItem<*>> {

        /**
         * The first time this is referenced, a call will be made to the DB to fetch the entire transaction.
         * So, refer to this only if you need more than just the [transactionId], [transactionDescription], or
         * [transactionTimestamp].
         *
         * The value cannot actually be `null`.
         */
        fun transaction(budgetId: UUID, accountIdToAccountMap: Map<UUID, Account>): Transaction =
            transaction
                ?: run {
                    budgetDao.getTransactionOrNull(transactionId, budgetId, accountIdToAccountMap)!!
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

    override fun close() {}

    fun getTransactionOrNull(
        transactionId: UUID,
        budgetId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): Transaction?

}
