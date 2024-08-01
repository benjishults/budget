package bps.budget.model

import bps.budget.persistence.AccountConfig
import java.math.BigDecimal
import java.util.UUID

// TODO consider creating all these accounts on first run.
const val defaultWalletAccountName = "Wallet"
const val defaultWalletAccountDescription = "Cash on hand"
const val defaultCheckingAccountName = "Checking"
const val defaultCheckingAccountDescription = "Account from which checks clear"
const val defaultFoodAccountName = "Food"
const val defaultFoodAccountDescription = "Food other than what's covered in entertainment."
const val defaultTransportationAccountName = "Transportation"
const val defaultTransportationAccountDescription = "Fuel, up-keep, fares, etc."
const val defaultTravelAccountName = "Travel"
const val defaultTravelAccountDescription = "Travel for vacation."
const val defaultEntertainmentAccountName = "Entertainment"
const val defaultEntertainmentAccountDescription = "Games, books, going out for food or fun."
const val defaultEducationAccountName = "Education"
const val defaultEducationAccountDescription = "Tuition, books, etc."
const val defaultNecessitiesAccountName = "Necessities"
const val defaultCheckingDraftsAccountName = "Checking Drafts"
const val defaultCheckingDraftsAccountDescription =
    "When a check is written or clears, a transaction occurs in this account."

sealed interface Account : AccountConfig {
//    val transactions: List<Transaction>

//    fun commit(transaction: Transaction)

}

abstract class BaseAccount(
    override var name: String,
    override var description: String = "",
    override var id: UUID = UUID.randomUUID(),
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
