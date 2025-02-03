package bps.budget.model

import bps.budget.persistence.AccountDao
import bps.budget.persistence.TransactionDao
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Clock
import java.math.BigDecimal
import java.util.UUID

/**
 * Currently not thread safe to add or delete accounts.  So, just be sure to use only a "main" thread.
 */
class BudgetData(
    val id: UUID,
    val name: String,
    var timeZone: TimeZone,
    var analyticsStart: Instant,
    val generalAccount: CategoryAccount,
    categoryAccounts: List<CategoryAccount>,
    realAccounts: List<RealAccount> = emptyList(),
    chargeAccounts: List<ChargeAccount> = emptyList(),
    draftAccounts: List<DraftAccount> = emptyList(),
) {

    var categoryAccounts: List<CategoryAccount> = categoryAccounts.sortedBy { it.name }
        private set

    var realAccounts: List<RealAccount> = realAccounts.sortedBy { it.name }
        private set

    var draftAccounts: List<DraftAccount> = draftAccounts.sortedBy { it.name }
        private set

    var chargeAccounts: List<ChargeAccount> = chargeAccounts.sortedBy { it.name }
        private set

    init {
        require(generalAccount in categoryAccounts) { "general account must be among category accounts" }
    }

    private val byId: MutableMap<UUID, Account> =
        (categoryAccounts + realAccounts + draftAccounts + chargeAccounts)
            .associateByTo(mutableMapOf()) {
                it.id
            }

    val accountIdToAccountMap: Map<UUID, Account>
        get() = byId

    @Suppress("UNCHECKED_CAST")
    fun <T : Account> getAccountByIdOrNull(id: UUID): T? =
        byId[id] as T?

    fun commit(transaction: Transaction) {
        transaction.allItems()
            .forEach { item: Transaction.Item<*> ->
                getAccountByIdOrNull<Account>(item.account.id)!!
                    .commit(item)
            }
    }

    /**
     * Balances sum up properly and there is a general account.
     */
    fun validate(): Boolean {
        val categoryAndDraftSum: BigDecimal =
            (categoryAccounts + draftAccounts)
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, account: Account ->
                    sum + account.balance
                }
        val realSum: BigDecimal =
            (realAccounts + chargeAccounts)
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, account: Account ->
                    sum + account.balance
                }
        return categoryAndDraftSum.setScale(2) == realSum.setScale(2) &&
                categoryAccounts.any { it.id == generalAccount.id }
    }

    override fun toString(): String =
        buildString {
            append("BudgetData($generalAccount")
            ((categoryAccounts - generalAccount) + realAccounts + chargeAccounts + draftAccounts)
                .forEach {
                    append(", $it")
                }
            append(")")
        }

    fun addChargeAccount(chargeAccount: ChargeAccount) {
        chargeAccounts = (chargeAccounts + chargeAccount).sortedBy { it.name }
        byId[chargeAccount.id] = chargeAccount
    }

    fun addCategoryAccount(categoryAccount: CategoryAccount) {
        categoryAccounts = (categoryAccounts + categoryAccount).sortedBy { it.name }
        byId[categoryAccount.id] = categoryAccount
    }

    fun addRealAccount(realAccount: RealAccount) {
        realAccounts = (realAccounts + realAccount).sortedBy { it.name }
        byId[realAccount.id] = realAccount
    }

    fun addDraftAccount(draftAccount: DraftAccount) {
        draftAccounts = (draftAccounts + draftAccount).sortedBy { it.name }
        byId[draftAccount.id] = draftAccount
    }

    fun deleteAccount(account: Account) {
        require(account.balance == BigDecimal.ZERO.setScale(2)) { "account balance must be zero before being deleted" }
        byId.remove(account.id)
        when (account) {
            is CategoryAccount -> {
                categoryAccounts = categoryAccounts - account
            }
            is ChargeAccount -> {
                chargeAccounts = chargeAccounts - account
            }
            is RealAccount -> {
                realAccounts = realAccounts - account
            }
            is DraftAccount -> {
                draftAccounts = draftAccounts - account
            }
        }
    }

    /**
     * NOTE: this allows you to delete a transaction that has already been cleared.  Callers should avoid making this
     *   mistake.
     *
     * This reverses the balance changes would have resulted from the application of this [transaction].  In other
     * words, this commits the [Transaction.Item.negate] of each of the [transaction]'s [Transaction.allItems].
     */
    fun undoTransaction(transaction: Transaction) {
        // if this transaction has already cleared?  error out.
        transaction
            .allItems()
            .map(Transaction.Item<*>::negate)
            .forEach { negatedTransactionItem ->
                negatedTransactionItem.account.commit(negatedTransactionItem)
            }
    }

    /**
     * NOTE: this allows you to delete a transaction that has already been cleared.  Callers should avoid making this
     *   mistake.
     *
     * This reverses the balance changes would have resulted from the application of the transaction associated
     * to this [transactionItem].  In other
     * words, this commits the [Transaction.Item.negate] of each of the [transactionItem]'s [Transaction.allItems].
     */
    fun undoTransactionForItem(transactionItem: TransactionDao.ExtendedTransactionItem<*>) {
        undoTransaction(transactionItem.transaction(id, accountIdToAccountMap))
    }

    companion object {

        @JvmStatic
        fun persistWithBasicAccounts(
            budgetName: String,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),

            checkingBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            walletBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            generalAccountId: UUID = UUID.randomUUID(),
            budgetId: UUID = UUID.randomUUID(),
            accountDao: AccountDao,
        ): BudgetData {
            val (checkingAccount, draftAccount) = accountDao.createRealAndDraftAccountOrNull(
                name = defaultCheckingAccountName,
                description = defaultCheckingAccountDescription,
                balance = checkingBalance,
                budgetId = budgetId,
            )!!
            val generalAccount = accountDao.createGeneralAccountWithIdOrNull(
                id = generalAccountId,
                balance = checkingBalance + walletBalance,
                budgetId = budgetId,
            )!!
            val wallet = accountDao.createRealAccountOrNull(
                name = defaultWalletAccountName,
                description = defaultWalletAccountDescription,
                balance = walletBalance,
                budgetId = budgetId,
            )!!
            return BudgetData(
                id = budgetId,
                name = budgetName,
                timeZone = timeZone,
                analyticsStart = Clock.System.now(),
                generalAccount = generalAccount,
                categoryAccounts = listOf(
                    generalAccount,
                    accountDao.createCategoryAccountOrNull(
                        defaultCosmeticsAccountName,
                        defaultCosmeticsAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultEducationAccountName,
                        defaultEducationAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultEntertainmentAccountName,
                        defaultEntertainmentAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultFoodAccountName,
                        defaultFoodAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultHobbyAccountName,
                        defaultHobbyAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultHomeAccountName,
                        defaultHomeAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultHousingAccountName,
                        defaultHousingAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultMedicalAccountName,
                        defaultMedicalAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultNecessitiesAccountName,
                        defaultNecessitiesAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultNetworkAccountName,
                        defaultNetworkAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultTransportationAccountName,
                        defaultTransportationAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultTravelAccountName,
                        defaultTravelAccountDescription,
                        budgetId = budgetId,
                    )!!,
                    accountDao.createCategoryAccountOrNull(
                        defaultWorkAccountName,
                        defaultWorkAccountDescription,
                        budgetId = budgetId,
                    )!!,
                ),
                realAccounts = listOf(
                    wallet,
                    checkingAccount,
                ),
                draftAccounts = listOf(draftAccount),
            )
        }

    }

}
