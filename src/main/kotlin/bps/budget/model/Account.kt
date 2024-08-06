package bps.budget.model

import java.math.BigDecimal
import java.util.UUID

// TODO consider creating all these accounts on first run.
const val defaultGeneralAccountName = "General"
const val defaultGeneralAccountDescription = "Income is automatically deposited here and allowances are made from here."
const val defaultWalletAccountName = "Wallet"
const val defaultWalletAccountDescription = "Cash on hand."
const val defaultCheckingAccountName = "Checking"
const val defaultCheckingAccountDescription = "Account from which checks clear."
const val defaultFoodAccountName = "Food"
const val defaultFoodAccountDescription = "Food other than what's covered in entertainment."
const val defaultTransportationAccountName = "Transportation"
const val defaultTransportationAccountDescription = "Vehicle payments, fuel, up-keep, fares, etc."
const val defaultTravelAccountName = "Travel"
const val defaultTravelAccountDescription = "Travel expenses for vacation."
const val defaultEntertainmentAccountName = "Entertainment"
const val defaultEntertainmentAccountDescription = "Games, books, subscriptions, going out for food or fun."
const val defaultEducationAccountName = "Education"
const val defaultEducationAccountDescription = "Tuition, books, etc."
const val defaultNetworkAccountName = "Network"
const val defaultNetworkAccountDescription = "Mobile plan, routers, internet access."
const val defaultWorkAccountName = "Work"
const val defaultWorkAccountDescription = "Work-related expenses (possibly to be reimbursed)."
const val defaultMedicalAccountName = "Medical"
const val defaultMedicalAccountDescription = "Medicine, supplies, insurance, etc."
const val defaultNecessitiesAccountName = "Necessities"
const val defaultNecessitiesAccountDescription = "Soap, light bulbs, etc."
const val defaultCheckingDraftsAccountName = "Checking Drafts"
const val defaultCheckingDraftsAccountDescription = "Records checks being written or clearing."


sealed class Account(
    override var name: String,
    override var description: String = "",
    override var id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : AccountData {
    override var balance: BigDecimal = balance
        protected set

    fun commit(item: Transaction.Item) {
        balance += item.amount
    }

    override fun toString(): String {
        return "Account('$name', $balance)"
    }

}

// TODO consider merging these into a single class.

class CategoryAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : Account(name, description, id, balance) {
    override fun toString(): String {
        return "Category${super.toString()}"
    }
}

class RealAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : Account(name, description, id, balance) {
    override fun toString(): String {
        return "Real${super.toString()}"
    }
}


class DraftAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    val realCompanion: RealAccount,
) : Account(name, description, id, balance) {
    override fun toString(): String {
        return "Draft${super.toString()}"
    }
}
