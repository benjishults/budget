package bps.budget.model

import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID

/**
 * Currently not thread safe to add or delete accounts.
 */
class BudgetData(
    val timeZone: TimeZone,
    val generalAccount: CategoryAccount,
    categoryAccounts: List<CategoryAccount>,
    realAccounts: List<RealAccount> = emptyList(),
    draftAccounts: List<DraftAccount> = emptyList(),
) {

    var categoryAccounts: List<CategoryAccount> = categoryAccounts.sortedBy { it.name }
        private set

    var realAccounts: List<RealAccount> = realAccounts.sortedBy { it.name }
        private set

    var draftAccounts: List<DraftAccount> = draftAccounts.sortedBy { it.name }
        private set

    private val byId: MutableMap<UUID, Account> =
        (categoryAccounts + realAccounts + draftAccounts)
            .associateByTo(mutableMapOf()) {
                it.id
            }

    fun addCategoryAccount(account: CategoryAccount) {
        categoryAccounts = categoryAccounts + account
        byId[account.id] = account
    }

    fun addRealAccount(account: RealAccount) {
        realAccounts = realAccounts + account
        byId[account.id] = account
    }

    fun addDraftAccount(account: DraftAccount) {
        draftAccounts = draftAccounts + account
        byId[account.id] = account
    }

    fun deleteCategoryAccount(account: CategoryAccount) {
        categoryAccounts = categoryAccounts - account
        byId.remove(account.id)
    }

    fun deleteRealAccount(account: RealAccount) {
        realAccounts = realAccounts - account
        byId.remove(account.id)
    }

    fun deleteDraftAccount(account: DraftAccount) {
        draftAccounts = draftAccounts - account
        byId.remove(account.id)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Account?> getAccountById(id: UUID): T =
        byId[id] as T

    fun commit(transaction: Transaction) {
        require(transaction.validate())
        transaction.categoryItems
            .forEach { item: Transaction.Item ->
                getAccountById<CategoryAccount>(item.categoryAccount!!.id)
                    .commit(item)
            }
        transaction.realItems
            .forEach { item: Transaction.Item ->
                getAccountById<RealAccount>(item.realAccount!!.id)
                    .commit(item)
            }
        transaction.draftItems
            .forEach { item: Transaction.Item ->
                getAccountById<DraftAccount>(item.draftAccount!!.id)
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
            realAccounts
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, account: Account ->
                    sum + account.balance
                }
        return categoryAndDraftSum.setScale(2) == realSum.setScale(2) &&
                categoryAccounts.any { it.id == generalAccount.id }
    }

    override fun toString(): String =
        buildString {
            append("BudgetData($generalAccount")
            ((categoryAccounts - generalAccount) + realAccounts + draftAccounts).forEach {
                append(", $it")
            }
            append(")")
        }

    companion object {

        @JvmStatic
        fun withBasicAccounts(
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
            checkingBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            walletBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            generalAccountId: UUID? = null,
        ): BudgetData {
            val checkingAccount = RealAccount(
                name = defaultCheckingAccountName,
                description = defaultCheckingAccountDescription,
                balance = checkingBalance,
            )
            val generalAccount = CategoryAccount(
                name = defaultGeneralAccountName,
                description = defaultGeneralAccountDescription,
                id = generalAccountId ?: UUID.randomUUID(),
                balance = checkingBalance + walletBalance,
            )
            val wallet = RealAccount(
                name = defaultWalletAccountName,
                description = defaultWalletAccountDescription,
                balance = walletBalance,
            )
            return BudgetData(
                timeZone,
                generalAccount,
                listOf(
                    generalAccount,
                    CategoryAccount(defaultNecessitiesAccountName, defaultNecessitiesAccountDescription),
                    CategoryAccount(defaultFoodAccountName, defaultFoodAccountDescription),
                    CategoryAccount(defaultEducationAccountName, defaultEducationAccountDescription),
                    CategoryAccount(defaultEntertainmentAccountName, defaultEntertainmentAccountDescription),
                    CategoryAccount(defaultMedicalAccountName, defaultMedicalAccountDescription),
                    CategoryAccount(defaultNetworkAccountName, defaultNetworkAccountDescription),
                    CategoryAccount(defaultTransportationAccountName, defaultTransportationAccountDescription),
                    CategoryAccount(defaultTravelAccountName, defaultTravelAccountDescription),
                    CategoryAccount(defaultWorkAccountName, defaultWorkAccountDescription),
                ),
                listOf(
                    wallet,
                    checkingAccount,
                ),
                listOf(
                    DraftAccount(
                        name = defaultCheckingDraftsAccountName,
                        description = defaultCheckingDraftsAccountDescription,
                        realCompanion = checkingAccount,
                    ),
                ),
            )
        }

    }

}
