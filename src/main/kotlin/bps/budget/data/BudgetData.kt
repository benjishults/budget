package bps.budget.data

import bps.budget.model.Account
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.transaction.Transaction
import bps.budget.ui.UiFunctions
import java.math.BigDecimal

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
    private val _transactions: MutableList<Transaction> = transactions.toMutableList()
//    val transactions: List<Transaction> = _transactions
//    private val byId: MutableMap<UUID, Account> = mutableMapOf()

    fun addRealAccount(account: RealAccount) {
        _realAccounts.add(account)
    }

    fun addCategoryAccount(account: CategoryAccount) {
        _categoryAccounts.add(account)
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
