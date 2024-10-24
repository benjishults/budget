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

abstract class Account(
    // TODO why are these vars?
    override var name: String,
    override var description: String = "",
    override var id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    open val type: String,
    val budgetId: UUID,
) : AccountData {

    override var balance: BigDecimal = balance
        protected set

    abstract fun Transaction.ItemBuilder.itemBuilderSetter(): Transaction.ItemBuilder

    /**
     * add this [Account] to the appropriate list of [Transaction.ItemBuilder]s within the [Transaction.Builder].
     */
    abstract fun Transaction.Builder.addForAccount(itemBuilder: Transaction.ItemBuilder): Unit

    fun commit(item: Transaction.Item) {
        balance += item.amount
    }

    fun Transaction.Builder.addItem(
        amount: BigDecimal,
        description: String? = null,
        draftStatus: DraftStatus = DraftStatus.none,
        id: UUID = UUID.randomUUID(),
    ) {
        addForAccount(
            Transaction
                .ItemBuilder(
                    id,
                    amount,
                    description,
                    draftStatus = draftStatus,
                )
                .itemBuilderSetter(),
        )
    }

    override fun toString(): String {
        return "${javaClass.name}('$name', $balance, id=$id, budgetId=$budgetId)"
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
    budgetId: UUID,
) : Account(name, description, id, balance, "category", budgetId) {

    override fun Transaction.ItemBuilder.itemBuilderSetter(): Transaction.ItemBuilder {
        categoryAccount = this@CategoryAccount
        return this
    }

    override fun Transaction.Builder.addForAccount(itemBuilder: Transaction.ItemBuilder) {
        categoryItemBuilders.add(itemBuilder)
    }

}

open class RealAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    budgetId: UUID,
) : Account(name, description, id, balance, "real", budgetId) {

    override fun Transaction.ItemBuilder.itemBuilderSetter(): Transaction.ItemBuilder {
        realAccount = this@RealAccount
        return this
    }

    override fun Transaction.Builder.addForAccount(itemBuilder: Transaction.ItemBuilder) {
        realItemBuilders.add(itemBuilder)
    }
}

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
    budgetId: UUID,
) : Account(name, description, id, balance, "draft", budgetId) {

    override fun Transaction.ItemBuilder.itemBuilderSetter(): Transaction.ItemBuilder {
        draftAccount = this@DraftAccount
        return this
    }

    override fun Transaction.Builder.addForAccount(itemBuilder: Transaction.ItemBuilder) {
        draftItemBuilders.add(itemBuilder)
    }
}

class ChargeAccount(
    name: String,
    description: String = "",
    id: UUID = UUID.randomUUID(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    budgetId: UUID,
) : RealAccount(name, description, id, balance, budgetId) {
    override val type: String = "charge"
    override fun Transaction.ItemBuilder.itemBuilderSetter(): Transaction.ItemBuilder {
        chargeAccount = this@ChargeAccount
        return this
    }

    override fun Transaction.Builder.addForAccount(itemBuilder: Transaction.ItemBuilder) {
        chargeItemBuilders.add(itemBuilder)
    }
}
