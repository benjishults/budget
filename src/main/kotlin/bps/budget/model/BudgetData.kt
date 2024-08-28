package bps.budget.model

import kotlinx.datetime.TimeZone
import java.math.BigDecimal
import java.util.UUID

/**
 * Currently not thread safe to add or delete accounts.  So, just be sure to use only a "main" thread.
 */
class BudgetData(
    val id: UUID,
    val name: String,
    val timeZone: TimeZone,
    val generalAccount: CategoryAccount,
    categoryAccounts: List<CategoryAccount>,
    realAccounts: List<RealAccount> = emptyList(),
    draftAccounts: List<DraftAccount> = emptyList(),
    chargeAccounts: List<ChargeAccount> = emptyList(),
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
        (categoryAccounts + realAccounts + draftAccounts)
            .associateByTo(mutableMapOf()) {
                it.id
            }

    @Suppress("UNCHECKED_CAST")
    fun <T : Account> getAccountByIdOrNull(id: UUID): T? =
        byId[id] as T?

    fun commit(transaction: Transaction) {
        transaction.categoryItems
            .forEach { item: Transaction.Item ->
                getAccountByIdOrNull<CategoryAccount>(item.categoryAccount!!.id)!!
                    .commit(item)
            }
        transaction.realItems
            .forEach { item: Transaction.Item ->
                getAccountByIdOrNull<RealAccount>(item.realAccount!!.id)!!
                    .commit(item)
            }
        transaction.draftItems
            .forEach { item: Transaction.Item ->
                getAccountByIdOrNull<DraftAccount>(item.draftAccount!!.id)!!
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
        chargeAccounts = chargeAccounts + chargeAccount
    }

    fun addCategoryAccount(categoryAccount: CategoryAccount) {
        categoryAccounts = categoryAccounts + categoryAccount
    }

    fun addRealAccount(realAccount: RealAccount) {
        realAccounts = realAccounts + realAccount
    }

    fun addDraftAccount(draftAccount: DraftAccount) {
        draftAccounts = draftAccounts + draftAccount
    }

    companion object {

        @JvmStatic
        fun withBasicAccounts(
            budgetName: String,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
            checkingBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            walletBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            generalAccountId: UUID? = null,
            budgetId: UUID? = null,
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
                id = budgetId ?: UUID.randomUUID(),
                name = budgetName,
                timeZone = timeZone,
                generalAccount = generalAccount,
                categoryAccounts = listOf(
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
                realAccounts = listOf(
                    wallet,
                    checkingAccount,
                ),
                draftAccounts = listOf(
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
