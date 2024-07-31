package bps.budget.model

import bps.budget.persistence.AccountConfig
import java.math.BigDecimal
import java.util.UUID

sealed interface Account : AccountConfig {
//    val transactions: List<Transaction>

//    fun commit(transaction: Transaction)

}

abstract class BaseAccount(
    override val name: String,
    override val description: String = "",
    override val id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO,
//    transactions: List<Transaction> = emptyList(),
) : Account {
    override var balance: BigDecimal = balance
        protected set
//    private val _transactions: MutableList<Transaction> = transactions.toMutableList()
//    override val transactions: List<Transaction>
//        get() = _transactions.toList()

//    override fun commit(transaction: Transaction) {
////        _transactions.add(transaction)
//        balance += transaction.amount
//    }

}

class CategoryAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO,
//    transactions: List<Transaction> = emptyList(),
) : BaseAccount(name, description, id, balance /*transactions*/)

class RealAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO,
    draftCompanion: DraftAccount? = null,
//    transactions: List<Transaction> = emptyList(),
) : BaseAccount(name, description, id, balance /*transactions*/) {

    var draftCompanion: DraftAccount? = draftCompanion
        private set

    /**
     * Can only be called once with a non-`null` value
     */
    fun setDraftCompanion(draftAccount: DraftAccount) {
        check(draftCompanion === null)
        draftCompanion = draftAccount
    }

}

class DraftAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO,
    val realCompanion: RealAccount,
//    transactions: List<Transaction> = emptyList(),
) : BaseAccount(name, description, id, balance /*transactions*/)
