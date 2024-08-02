package bps.budget.model

import bps.budget.transaction.TransactionItem
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

// TODO consider whether it makes sense for this to inherit from AccountConfig
sealed interface Account : AccountData {
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

    fun commit(item: TransactionItem) {
        balance += item.amount
    }

    override fun toString(): String {
        return "Account($name, $balance)"
    }
//    private val _transactions: MutableList<Transaction> = transactions.toMutableList()
//    override val transactions: List<Transaction>
//        get() = _transactions.toList()

}

class CategoryAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO,
//    transactions: List<Transaction> = emptyList(),
) : BaseAccount(name, description, id, balance /*transactions*/) {
    override fun toString(): String {
        return "Category${super.toString()}"
    }
}

class RealAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO,
//    transactions: List<Transaction> = emptyList(),
) : BaseAccount(name, description, id, balance /*transactions*/) {
    override fun toString(): String {
        return "Real${super.toString()}"
    }
}


class DraftAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO,
    val realCompanion: RealAccount,
//    transactions: List<Transaction> = emptyList(),
) : BaseAccount(name, description, id, balance /*transactions*/) {
    override fun toString(): String {
        return "Draft${super.toString()}"
    }
}
