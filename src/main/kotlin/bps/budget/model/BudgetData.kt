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

    fun deleteChargeAccount(chargeAccount: ChargeAccount) {
        require(chargeAccount.balance == BigDecimal.ZERO.setScale(2)) { "chargeAccount balance must be zero" }
        chargeAccounts = chargeAccounts - chargeAccount
        byId.remove(chargeAccount.id)
    }

    fun deleteRealAccount(realAccount: RealAccount) {
        require(realAccount.balance == BigDecimal.ZERO.setScale(2)) { "realAccount balance must be zero" }
        realAccounts = realAccounts - realAccount
        byId.remove(realAccount.id)
    }

    fun deleteCategoryAccount(categoryAccount: CategoryAccount) {
        require(categoryAccount.balance == BigDecimal.ZERO.setScale(2)) { "categoryAccount balance must be zero" }
        categoryAccounts = categoryAccounts - categoryAccount
        byId.remove(categoryAccount.id)
    }

    fun deleteDraftAccount(draftAccount: DraftAccount) {
        require(draftAccount.balance == BigDecimal.ZERO.setScale(2)) { "draftAccount balance must be zero" }
        draftAccounts = draftAccounts - draftAccount
        byId.remove(draftAccount.id)
    }

    companion object {

        @JvmStatic
        fun withBasicAccounts(
            budgetName: String,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
            checkingBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            walletBalance: BigDecimal = BigDecimal.ZERO.setScale(2),
            generalAccountId: UUID? = null,
            budgetId: UUID = UUID.randomUUID(),
        ): BudgetData {
            val checkingAccount = RealAccount(
                name = defaultCheckingAccountName,
                description = defaultCheckingAccountDescription,
                balance = checkingBalance,
                budgetId = budgetId,
            )
            val generalAccount = CategoryAccount(
                name = defaultGeneralAccountName,
                description = defaultGeneralAccountDescription,
                id = generalAccountId ?: UUID.randomUUID(),
                balance = checkingBalance + walletBalance,
                budgetId = budgetId,
            )
            val wallet = RealAccount(
                name = defaultWalletAccountName,
                description = defaultWalletAccountDescription,
                balance = walletBalance,
                budgetId = budgetId,
            )
            return BudgetData(
                id = budgetId,
                name = budgetName,
                timeZone = timeZone,
                generalAccount = generalAccount,
                categoryAccounts = listOf(
                    generalAccount,
                    CategoryAccount(
                        defaultCosmeticsAccountName,
                        defaultCosmeticsAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultEducationAccountName,
                        defaultEducationAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultEntertainmentAccountName,
                        defaultEntertainmentAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultFoodAccountName,
                        defaultFoodAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultHobbyAccountName,
                        defaultHobbyAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultHomeAccountName,
                        defaultHomeAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultHousingAccountName,
                        defaultHousingAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultMedicalAccountName,
                        defaultMedicalAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultNecessitiesAccountName,
                        defaultNecessitiesAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultNetworkAccountName,
                        defaultNetworkAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultTransportationAccountName,
                        defaultTransportationAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultTravelAccountName,
                        defaultTravelAccountDescription,
                        budgetId = budgetId,
                    ),
                    CategoryAccount(
                        defaultWorkAccountName,
                        defaultWorkAccountDescription,
                        budgetId = budgetId,
                    ),
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
                        budgetId = budgetId,
                    ),
                ),
            )
        }

    }

}
