@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.model

import bps.budget.persistence.AccountEntity
import java.math.BigDecimal
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO consider creating all these accounts on first run
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

// TODO See GenericFunctionTest.kt for a discussion of how I want to improve the generic typing here
//      However, since I'm not using those polymorphic builders anymore, this may not be a thing anymore.
// TODO I should probably have these account classes cache the transaction items on each account?  ... or just cache
//      transactions as they're pulled and have a map of transactionId -> transaction like what I'm doing for accounts.
//      ... but I would need to keep that cache well-pruned and up-to-date...  or both  ...
//      Yeah, keep a map of transactionId -> transaction and each account caches [AccountTransactionEntity]s
//      https://github.com/benjishults/budget-kmp/issues/58
abstract class Account(
    // TODO why are these vars?
    override var name: String,
    override var description: String = "",
    override val id: Uuid = Uuid.random(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    override val type: String,
    val budgetId: Uuid,
) : AccountData {

    override var balance: BigDecimal = balance
        protected set

    open fun Transaction.Builder.addItemBuilderTo(
        amount: BigDecimal,
        description: String? = null,
        draftStatus: DraftStatus = DraftStatus.none,
        id: Uuid = Uuid.random(),
    ): Unit = TODO()

    open fun itemBuilderFactory(
        id: Uuid,
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
    ): Transaction.ItemBuilder<*> =
        TODO()

    fun addAmount(amount: BigDecimal) {
        balance += amount
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
    id: Uuid = Uuid.random(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    budgetId: Uuid,
) : Account(name, description, id, balance, AccountType.category.name, budgetId) {

    override fun Transaction.Builder.addItemBuilderTo(
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
        id: Uuid,
    ) {
        categoryItemBuilders.add(itemBuilderFactory(id, amount, description, draftStatus))
    }

    override fun itemBuilderFactory(
        id: Uuid,
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
    ): Transaction.ItemBuilder<CategoryAccount> =
        Transaction.ItemBuilder(
            account = this,
            id = id,
            amount = amount,
            description = description,
            draftStatus = draftStatus,
        )

}

fun AccountEntity.toCategoryAccount(): CategoryAccount? =
    if (type == AccountType.category.name) {
        CategoryAccount(
            name = name,
            description = description,
            id = id,
            balance = balance,
            budgetId = budgetId,
        )
    } else null

open class RealAccount(
    name: String,
    description: String = "",
    id: Uuid = Uuid.random(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    budgetId: Uuid,
) : Account(name, description, id, balance, AccountType.real.name, budgetId) {

    override fun Transaction.Builder.addItemBuilderTo(
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
        id: Uuid,
    ) {
        realItemBuilders.add(itemBuilderFactory(id, amount, description, draftStatus))
    }

    override fun itemBuilderFactory(
        id: Uuid,
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
    ): Transaction.ItemBuilder<RealAccount> =
        Transaction.ItemBuilder(
            account = this,
            id = id,
            amount = amount,
            description = description,
            draftStatus = draftStatus,
        )

}

fun AccountEntity.toRealAccount(): RealAccount? =
    if (type == AccountType.real.name) {
        RealAccount(
            name = name,
            description = description,
            id = id,
            balance = balance,
            budgetId = budgetId,
        )
    } else
        null

/**
 * A separate [DraftAccount] is useful for quickly determining the outstanding balance.  One only has to look at the
 * balance on this account to compute the draft balance of the companion real account.
 */
class DraftAccount(
    name: String,
    description: String = "",
    id: Uuid = Uuid.random(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    val realCompanion: RealAccount,
    budgetId: Uuid,
) : Account(name, description, id, balance, AccountType.draft.name, budgetId) {

    override fun Transaction.Builder.addItemBuilderTo(
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
        id: Uuid,
    ) {
        draftItemBuilders.add(itemBuilderFactory(id, amount, description, draftStatus))
    }

    override fun itemBuilderFactory(
        id: Uuid,
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
    ): Transaction.ItemBuilder<DraftAccount> =
        Transaction.ItemBuilder(
            account = this,
            id = id,
            amount = amount,
            description = description,
            draftStatus = draftStatus,
        )

}

fun AccountEntity.toDraftAccount(accountIdToAccountMap: (Uuid) -> Account?): DraftAccount? =
    if (type == AccountType.draft.name) {
        DraftAccount(
            name = name,
            description = description,
            id = id,
            balance = balance,
            budgetId = budgetId,
            realCompanion = accountIdToAccountMap(companionId!!)!! as RealAccount,
        )
    } else
        null

class ChargeAccount(
    name: String,
    description: String = "",
    id: Uuid = Uuid.random(),
    balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    budgetId: Uuid,
) : RealAccount(name, description, id, balance, budgetId) {

    override val type: String = AccountType.charge.name

    override fun Transaction.Builder.addItemBuilderTo(
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
        id: Uuid,
    ) {
        chargeItemBuilders.add(itemBuilderFactory(id, amount, description, draftStatus))
    }

    override fun itemBuilderFactory(
        id: Uuid,
        amount: BigDecimal,
        description: String?,
        draftStatus: DraftStatus,
    ): Transaction.ItemBuilder<ChargeAccount> =
        Transaction.ItemBuilder(
            account = this,
            id = id,
            amount = amount,
            description = description,
            draftStatus = draftStatus,
        )

}

fun AccountEntity.toChargeAccount(): ChargeAccount? =
    if (type == AccountType.charge.name) {
        ChargeAccount(
            name = name,
            description = description,
            id = id,
            balance = balance,
            budgetId = budgetId,
        )
    } else
        null
