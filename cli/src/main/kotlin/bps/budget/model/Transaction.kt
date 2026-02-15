@file:OptIn(ExperimentalUuidApi::class)

package bps.budget.model

import bps.budget.persistence.AccountDao.AccountCommitableTransactionItem
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.TransactionData
import kotlinx.datetime.Instant
import java.math.BigDecimal
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ConsistentCopyVisibility
data class Transaction private constructor(
    override val id: Uuid,
    override val description: String,
    override val timestamp: Instant,
    override val transactionType: String,
    val clears: Transaction? = null,
) : TransactionData {

    lateinit var categoryItems: List<Item<CategoryAccount>>
        private set
    lateinit var realItems: List<Item<RealAccount>>
        private set
    lateinit var chargeItems: List<Item<ChargeAccount>>
        private set
    lateinit var draftItems: List<Item<DraftAccount>>
        private set

    fun allItems(): List<Item<*>> = categoryItems + realItems + chargeItems + draftItems

    override fun compareTo(other: TransactionData): Int =
        this.timestamp.compareTo(other.timestamp)
            .let {
                when (it) {
                    0 -> this.id.toString().compareTo(other.id.toString())
                    else -> it
                }
            }

    private fun validate(): Boolean {
        val categoryAndDraftSum: BigDecimal =
            (categoryItems + draftItems)
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: Item<*> ->
                    sum + item.amount
                }
        val realSum: BigDecimal =
            (realItems + chargeItems)
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: Item<*> ->
                    sum + item.amount
                }
        return categoryAndDraftSum == realSum
    }

    private fun populate(
        categoryItems: List<Item<CategoryAccount>>,
        realItems: List<Item<RealAccount>>,
        chargeItems: List<Item<ChargeAccount>>,
        draftItems: List<Item<DraftAccount>>,
    ) {
        this.categoryItems = categoryItems
        this.realItems = realItems
        this.chargeItems = chargeItems
        this.draftItems = draftItems
        require(validate()) { "attempt was made to create invalid transaction: $this" }
    }

    /**
     * See src/test/kotlin/bps/kotlin/GenericFunctionTest.kt for a discussion of how I want to improve this
     */
    inner class Item<out A : Account>(
        val id: Uuid,
        override val amount: BigDecimal,
        val description: String? = null,
        val account: A,
        val draftStatus: DraftStatus = DraftStatus.none,
    ) : Comparable<Item<*>>, AccountCommitableTransactionItem {

        val transaction = this@Transaction

        val timestamp: Instant = transaction.timestamp
        override val accountId: Uuid = account.id

        fun toTransactionDaoItem(): TransactionDao.TransactionItem =
            TransactionDao.TransactionItem(
                amount = amount,
                description = description,
                accountId = account.id,
                accountType = account.type,
                draftStatus = draftStatus.name,
            )

        override fun compareTo(other: Item<*>): Int =
            transaction.timestamp
                .compareTo(other.transaction.timestamp)
                .takeIf { it != 0 }
                ?: account.name
                    .compareTo(other.account.name)
                    .takeIf { it != 0 }
                ?: (description ?: transaction.description)
                    .compareTo(other.description ?: other.transaction.description)

        override fun toString(): String =
            "TransactionItem($account, $amount${
                if (description?.isNotBlank() == true)
                    ", '$description'"
                else
                    ""
            })"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Item<*>) return false

            return id != other.id
        }

        override fun hashCode(): Int = id.hashCode()
        fun negate(): Item<A> =
            Item(
                id = id,
                amount = -amount,
                description = "NEGATE! $description",
                account = account,
                draftStatus = draftStatus,
            )

    }

    /**
     * See src/test/kotlin/bps/kotlin/GenericFunctionTest.kt for a discussion of how I want to improve this
     */
    class ItemBuilder<out A : Account>(
        val id: Uuid,
        val amount: BigDecimal,
        var description: String? = null,
        val account: A,
        var draftStatus: DraftStatus = DraftStatus.none,
    ) {
        // TODO make this an extension function of Transaction?
        fun build(transaction: Transaction): Item<A> =
            transaction.Item(
                id,
                amount,
                description,
                account,
                draftStatus,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ItemBuilder<*>) return false

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "ItemBuilder(description=$description, amount=$amount, id=$id, draftStatus=$draftStatus)"
        }

    }

    class Builder(
        var description: String? = null,
        var timestamp: Instant? = null,
        var id: Uuid? = null,
        var transactionType: String? = null,
        var clears: Transaction? = null,
    ) {
        val categoryItemBuilders: MutableList<ItemBuilder<CategoryAccount>> = mutableListOf()
        val realItemBuilders: MutableList<ItemBuilder<RealAccount>> = mutableListOf()
        val chargeItemBuilders: MutableList<ItemBuilder<ChargeAccount>> = mutableListOf()
        val draftItemBuilders: MutableList<ItemBuilder<DraftAccount>> = mutableListOf()

        fun build(): Transaction = Transaction(
            id = this@Builder.id ?: Uuid.random(),
            description = this@Builder.description!!,
            timestamp = this@Builder.timestamp!!,
            transactionType = this@Builder.transactionType!!,
            clears = this@Builder.clears,
        )
            .apply {
                populate(
                    this@Builder
                        .categoryItemBuilders
                        .map { it.build(this) },
                    this@Builder
                        .realItemBuilders
                        .map { it.build(this) },
                    this@Builder
                        .chargeItemBuilders
                        .map { it.build(this) },
                    this@Builder
                        .draftItemBuilders
                        .map { it.build(this) },
                )
            }
    }
}

