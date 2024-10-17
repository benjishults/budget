package bps.budget.model

import java.math.BigDecimal
import java.util.UUID

// TODO consider creating all these accounts on first run
const val defaultGeneralAccountName = "General"
const val defaultGeneralAccountDescription = "Income is automatically deposited here and allowances are made from here"
const val defaultWalletAccountName = "Wallet"
const val defaultWalletAccountDescription = "Cash on hand"
const val defaultCheckingAccountName = "Checking"
const val defaultCheckingAccountDescription = "Account from which checks clear"

const val defaultCosmeticsAccountName = "Cosmetics"
const val defaultCosmeticsAccountDescription = "Cosmetics, procedures, pampering, and accessories"
const val defaultEducationAccountName = "Education"
const val defaultEducationAccountDescription = "Tuition, books, etc."
const val defaultEntertainmentAccountName = "Entertainment"
const val defaultEntertainmentAccountDescription = "Games, books, subscriptions, going out for food or fun"
const val defaultFoodAccountName = "Food"
const val defaultFoodAccountDescription = "Food other than what's covered in entertainment"
const val defaultHobbyAccountName = "Hobby"
const val defaultHobbyAccountDescription = "Expenses related to a hobby"
const val defaultHomeAccountName = "Home Upkeep"
const val defaultHomeAccountDescription = "Upkeep: association fees, furnace filters, appliances, repairs, lawn care"
const val defaultHousingAccountName = "Housing"
const val defaultHousingAccountDescription = "Rent, mortgage, property tax, insurance"
const val defaultMedicalAccountName = "Medical"
const val defaultMedicalAccountDescription = "Medicine, supplies, insurance, etc."
const val defaultNecessitiesAccountName = "Necessities"
const val defaultNecessitiesAccountDescription = "Energy, water, cleaning supplies, soap, tooth brushes, etc."
const val defaultNetworkAccountName = "Network"
const val defaultNetworkAccountDescription = "Mobile plan, routers, internet access"
const val defaultTransportationAccountName = "Transportation"
const val defaultTransportationAccountDescription = "Fares, vehicle payments, insurance, fuel, up-keep, etc."
const val defaultTravelAccountName = "Travel"
const val defaultTravelAccountDescription = "Travel expenses for vacation"
const val defaultWorkAccountName = "Work"
const val defaultWorkAccountDescription = "Work-related expenses (possibly to be reimbursed)"
const val defaultCheckingDraftsAccountName = "Checking Drafts"
const val defaultCheckingDraftsAccountDescription = "Records checks being written or clearing"

open class Account(
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
        return "${javaClass.name}('$name', $balance)"
    }

    override fun equals(other: Any?): Boolean =
        if (this === other)
            true
        else if (other !is Account)
            false
        else if (id != other.id)
            false
        else
            true

    override fun hashCode(): Int {
        return id.hashCode()
    }

}

// TODO consider merging (most of) these into a single class.

class CategoryAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : Account(name, description, id, balance)

open class RealAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : Account(name, description, id, balance)

/**
 * A separate [DraftAccount] is useful for quickly determining the outstanding balance.  One only has to look at the
 * balance on this account to compute the draft balance of the companion real account.
 */
class DraftAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    val realCompanion: RealAccount,
) : Account(name, description, id, balance)

class ChargeAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : RealAccount(name, description, id, balance)
