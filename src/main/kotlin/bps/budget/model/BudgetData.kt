package bps.budget.model

import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.ui.UiFacade
import java.math.BigDecimal
import java.util.UUID

class BudgetData(
    val generalAccount: CategoryAccount,
    categoryAccounts: List<CategoryAccount>,
    realAccounts: List<RealAccount> = emptyList(),
    draftAccounts: List<DraftAccount> = emptyList(),
) {

    private val _categoryAccounts: MutableList<CategoryAccount> = categoryAccounts.toMutableList()
    val categoryAccounts: List<CategoryAccount> = _categoryAccounts
    private val _realAccounts: MutableList<RealAccount> = realAccounts.toMutableList()
    val realAccounts: List<RealAccount> = _realAccounts
    private val _draftAccounts: MutableList<DraftAccount> = draftAccounts.toMutableList()
    val draftAccounts: List<DraftAccount> = _draftAccounts

    private val byId: MutableMap<UUID, Account> = mutableMapOf()

    fun addCategoryAccount(account: CategoryAccount) {
        _categoryAccounts.add(account)
        byId[account.id] = account
    }

    fun addRealAccount(account: RealAccount) {
        _realAccounts.add(account)
        byId[account.id] = account
    }

    fun addDraftAccount(account: DraftAccount) {
        _draftAccounts.add(account)
        byId[account.id] = account
    }

    fun deleteCategoryAccount(account: CategoryAccount) {
        _categoryAccounts.remove(account)
        byId.remove(account.id)
    }

    fun commit(transaction: Transaction) {
        require(transaction.validate())
        transaction.categoryItems
            .forEach { item: TransactionItem ->
                _categoryAccounts
                    .find { account: CategoryAccount ->
                        account.id == item.categoryAccount!!.id
                    }!!
                    .commit(item)
            }
        transaction.realItems
            .forEach { item: TransactionItem ->
                _realAccounts
                    // TODO consider putting a map from ID to object in the BudgetData
                    .find { account: RealAccount ->
                        account.id == item.realAccount!!.id
                    }!!
                    .commit(item)
            }
        transaction.draftItems
            .forEach { item: TransactionItem ->
                _draftAccounts
                    .find { account: DraftAccount ->
                        account.id == item.draftAccount!!.id
                    }!!
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

        /**
         * Will build basic data if there is an error getting it from persisted data.
         */
        operator fun invoke(
            uiFacade: UiFacade,
            budgetDao: BudgetDao<*>,
        ): BudgetData =
            try {
                with(budgetDao) {
                    prepForFirstLoad()
                    load()
                }
            } catch (ex: DataConfigurationException) {
                if (uiFacade.userWantsBasicAccounts()) {
                    uiFacade.info(
                        """You'll be able to rename these accounts and create new accounts later,
                        |but please answer a couple of questions as we get started.""".trimMargin(),
                    )
                    withBasicAccounts(
                        checkingBalance = uiFacade.getInitialBalance(
                            "Checking",
                            "this is any account on which you are able to write checks",
                        ),
                        walletBalance = uiFacade.getInitialBalance(
                            "Wallet",
                            "this is cash you might carry on your person",
                        ),
                    )
                        .also { budgetData: BudgetData ->
                            uiFacade.info("saving that data...")
                            budgetDao.save(budgetData)
                            uiFacade.info(
                                """
                                    |Saved
                                    |Next, you'll probably want to
                                    |1) create more accounts (Savings, Credit Cards, etc.)
                                    |2) rename the 'Checking' account to specify your bank name
                                    |3) allocate money from your 'General' account into your category accounts
                            """.trimMargin(),
                            )
                        }
                } else {
                    uiFacade.createGeneralAccount(budgetDao)
                        .let { generalAccount: CategoryAccount ->
                            BudgetData(generalAccount, listOf(generalAccount))
                                .also { budgetData: BudgetData ->
                                    budgetDao.save(budgetData)
                                }
                        }
                }
            }
    }

}
