package bps.budget.persistence

import bps.budget.auth.User
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.util.UUID

///**
// * @param C the [BudgetConfig] type determining how the data is found in the first place.
// */
interface BudgetDao : AutoCloseable {

    val userBudgetDao: UserBudgetDao get() = TODO()
    val transactionDao: TransactionDao get() = TODO()
    val accountDao: AccountDao get() = TODO()

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

    override fun close() {}

}

interface UserBudgetDao {
    fun getUserByLogin(login: String): User? = null
    fun createUser(login: String, password: String): UUID = TODO()
    fun deleteBudget(budgetId: UUID) {}
    fun deleteUser(userId: UUID) {}
    fun deleteUserByLogin(login: String) {}
}

interface TransactionDao {

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
        val transactionDao: TransactionDao,
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

interface AccountDao {

    /**
     * The default implementation throws [NotImplementedError]
     */
    fun <T : Account> getDeactivatedAccounts(
        type: String,
        budgetId: UUID,
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
    ): List<T> = TODO()

    /**
     * The default implementation calls [getActiveAccounts] and [getDeactivatedAccounts] and pulls the
     * [Account.name]s out.  Implementors could improve on the efficiency if desired.
     */
    fun getAllAccountNamesForBudget(budgetId: UUID): List<String> =
        buildList {
            mapOf(
                "category" to ::CategoryAccount,
                "real" to ::RealAccount,
                "charge" to ::ChargeAccount,
            )
                .forEach { (type, factory) ->
                    addAll(
                        getActiveAccounts(type, budgetId, factory)
                            .map { it.name },
                    )
                    addAll(
                        getDeactivatedAccounts(type, budgetId, factory)
                            .map { it.name },
                    )
                }
        }

    /**
     * The default implementation throws [NotImplementedError]
     */
    fun deactivateAccount(account: Account): Unit = TODO()

    /**
     * The default implementation throws [NotImplementedError]
     */
    fun <T : Account> getActiveAccounts(
        type: String,
        budgetId: UUID,
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
    ): List<T> = TODO()

    fun updateBalances(transaction: Transaction, budgetId: UUID)
    fun updateAccount(account: Account): Boolean

}
