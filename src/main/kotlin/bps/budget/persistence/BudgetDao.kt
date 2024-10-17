package bps.budget.persistence

import bps.budget.auth.User
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.Transaction
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * @param C the [BudgetConfig] type determining how the data is found in the first place.
 */
interface BudgetDao/*<out C : BudgetConfigLookup>*/ : AutoCloseable {
//    val config: C

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
    fun commit(transaction: Transaction, budgetId: UUID) {}
    fun clearCheck(
        draftTransactionItems: List<Transaction.Item>,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) = Unit

    fun clearCheck(
        draftTransactionItem: Transaction.Item,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) =
        clearCheck(listOf(draftTransactionItem), clearingTransaction, budgetId)

    fun commitCreditCardPayment(
        clearedItems: List<ExtendedTransactionItem>,
        billPayTransaction: Transaction,
        budgetId: UUID,
    ) {
    }

    fun deleteAccount(account: Account) {}
    fun deleteBudget(budgetId: UUID) {}
    fun deleteUser(userId: UUID) {}
    fun deleteUserByLogin(login: String) {}

    class ExtendedTransactionItem(
        val item: Transaction.ItemBuilder,
        val transactionId: UUID,
        val transactionDescription: String,
        val transactionTimestamp: Instant,
        val budgetDao: BudgetDao,
        val budgetData: BudgetData,
    ) : Comparable<ExtendedTransactionItem> {

        /**
         * The first time this is referenced, a call will be made to the DB to fetch the entire transaction.
         * So, refer to this only if you need more than just the [transactionId], [transactionDescription], or
         * [transactionTimestamp].
         *
         * The value cannot actually be `null`.
         */
        var transaction: Transaction? = null
            get() =
                field
                    ?: run {
                        budgetDao.getTransactionOrNull(transactionId, budgetData)!!
                            .also {
                                field = it
                            }
                    }


        override fun compareTo(other: ExtendedTransactionItem): Int =
            this.transactionTimestamp.compareTo(other.transactionTimestamp)
                .let {
                    when (it) {
                        0 -> this.transactionId.compareTo(other.transactionId)
                        else -> it
                    }
                }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExtendedTransactionItem) return false

            if (item != other.item) return false

            return true
        }

        override fun hashCode(): Int {
            return item.hashCode()
        }
    }

    fun Account.fetchTransactionItemsInvolvingAccount(
        budgetData: BudgetData,
        limit: Int = 30,
        offset: Int = 0,
    ): List<ExtendedTransactionItem> =
        emptyList()

    override fun close() {}
    fun getTransactionOrNull(transactionId: UUID, budgetData: BudgetData): Transaction? = null

}
