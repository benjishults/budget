package bps.budget.data

import bps.budget.model.Account
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.transaction.Transaction
import bps.budget.transaction.TransactionItem
import bps.budget.ui.UiFunctions
import java.math.BigDecimal
import java.util.UUID

class BudgetData(
    val generalAccount: CategoryAccount,
    categoryAccounts: List<CategoryAccount>,
    realAccounts: List<RealAccount> = emptyList(),
    draftAccounts: List<DraftAccount> = emptyList(),
    transactions: List<Transaction> = emptyList(),
) {

    private val _categoryAccounts: MutableList<CategoryAccount> = categoryAccounts.toMutableList()
    val categoryAccounts: List<CategoryAccount> = _categoryAccounts
    private val _realAccounts: MutableList<RealAccount> = realAccounts.toMutableList()
    val realAccounts: List<RealAccount> = _realAccounts
    private val _draftAccounts: MutableList<DraftAccount> = draftAccounts.toMutableList()
    val draftAccounts: List<DraftAccount> = _draftAccounts

    //    private val _transactions: MutableList<Transaction> = transactions.toMutableList()
//    val transactions: List<Transaction> = _transactions
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
                .fold(BigDecimal.ZERO) { sum: BigDecimal, account: Account ->
                    sum + account.balance
                }
        val realSum: BigDecimal =
            realAccounts
                .fold(BigDecimal.ZERO) { sum: BigDecimal, account: Account ->
                    sum + account.balance
                }
        return categoryAndDraftSum.setScale(2) == realSum.setScale(2) &&
                categoryAccounts.any { it.id == generalAccount.id }
    }

    companion object {

        /**
         * Will build basic data if there is an error getting it from a file.
         */
        operator fun invoke(
            uiFunctions: UiFunctions,
            budgetDao: BudgetDao<*>,
        ): BudgetData =
            try {
                with(budgetDao) {
                    prepForFirstLoad()
                    load()
                }
            } catch (ex: DataConfigurationException) {
                uiFunctions.createGeneralAccount(budgetDao)
                    .let { generalAccount: CategoryAccount ->
                        BudgetData(generalAccount, listOf(generalAccount))
                            .also { budgetData: BudgetData ->
                                budgetDao.save(budgetData)
                            }
                    }
            }
    }

}
