package bps.budget.model

import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.util.UUID

data class Transaction private constructor(
    val id: UUID,
    val description: String,
    val timestamp: Instant,
    val clears: Transaction? = null,
) {

    lateinit var categoryItems: List<Item>
        private set
    lateinit var realItems: List<Item>
        private set
    lateinit var chargeItems: List<Item>
        private set
    lateinit var draftItems: List<Item>
        private set

    private fun validate(): Boolean {
        val categoryAndDraftSum: BigDecimal =
            (categoryItems + draftItems)
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: Item ->
                    sum + item.amount
                }
        val realSum: BigDecimal =
            (realItems + chargeItems)
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: Item ->
                    sum + item.amount
                }
        return categoryAndDraftSum == realSum
    }

    private fun populate(
        categoryItems: List<Item>,
        realItems: List<Item>,
        chargeItems: List<Item>,
        draftItems: List<Item>,
    ) {
        this.categoryItems = categoryItems
        this.realItems = realItems
        this.chargeItems = chargeItems
        this.draftItems = draftItems
        require(validate()) { "attempt was made to create invalid transaction: $this" }
    }

    inner class Item(
        val id: UUID,
        val amount: BigDecimal,
        val description: String? = null,
        val categoryAccount: CategoryAccount? = null,
        val realAccount: RealAccount? = null,
        val chargeAccount: ChargeAccount? = null,
        val draftAccount: DraftAccount? = null,
        val draftStatus: DraftStatus = DraftStatus.none,
    ) : Comparable<Item> {

        val transaction = this@Transaction

        override fun compareTo(other: Item): Int =
            transaction.timestamp
                .compareTo(other.transaction.timestamp)
                .takeIf { it != 0 }
                ?: (draftAccount ?: realAccount ?: chargeAccount ?: categoryAccount)!!.name
                    .compareTo(
                        (other.draftAccount ?: other.realAccount ?: other.chargeAccount
                        ?: other.categoryAccount)!!.name,
                    )
                    .takeIf { it != 0 }
                ?: (description ?: transaction.description)
                    .compareTo(other.description ?: other.transaction.description)

        override fun toString(): String =
            "TransactionItem(${categoryAccount ?: realAccount ?: chargeAccount ?: draftAccount}, $amount${
                if (description?.isNotBlank() == true)
                    ", '$description'"
                else
                    ""
            })"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Item) return false

            return id != other.id
        }

        override fun hashCode(): Int = id.hashCode()

    }

    class ItemBuilder(
        val id: UUID,
        val amount: BigDecimal,
        var description: String? = null,
        var categoryAccount: CategoryAccount? = null,
        var realAccount: RealAccount? = null,
        var chargeAccount: ChargeAccount? = null,
        var draftAccount: DraftAccount? = null,
        val draftStatus: DraftStatus = DraftStatus.none,
    ) {
        // TODO make this an extension function of Transaction?
        fun build(transaction: Transaction): Item =
            transaction.Item(
                id,
                amount,
                description,
                categoryAccount,
                realAccount,
                chargeAccount,
                draftAccount,
                draftStatus,
            )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ItemBuilder) return false

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
        var id: UUID? = null,
        var clears: Transaction? = null,
    ) {
        val categoryItemBuilders: MutableList<ItemBuilder> = mutableListOf()
        val realItemBuilders: MutableList<ItemBuilder> = mutableListOf()
        val chargeItemBuilders: MutableList<ItemBuilder> = mutableListOf()
        val draftItemBuilders: MutableList<ItemBuilder> = mutableListOf()

//        fun toContextString(type: String): String =
//            """
//
//            """.trimIndent()

        fun build(): Transaction = Transaction(
            id = this@Builder.id ?: UUID.randomUUID(),
            description = this@Builder.description!!,
            timestamp = this@Builder.timestamp!!,
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

/**
 * [DraftAccount]s and [ChargeAccount]s have some transaction items that are outstanding and some that are cleared.
 * [RealAccount]s have "clearing" transaction items that clear a check or pay a charge bill.
 */
enum class DraftStatus {
    none,

    /**
     * Means that the item is a cleared draft or charge.
     */
    cleared,

    /**
     * Means that it is part of a clearing transaction on a real account.
     */
    clearing,

    /**
     * Means that the item is waiting for a clearing event on a real account.
     */
    outstanding
}
