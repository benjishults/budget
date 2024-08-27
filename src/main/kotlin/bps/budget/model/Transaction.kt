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
    lateinit var draftItems: List<Item>
        private set

    private fun validate(): Boolean {
        val categoryAndDraftSum: BigDecimal =
            (categoryItems + draftItems)
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: Item ->
                    sum + item.amount
                }
        val realSum: BigDecimal =
            realItems
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: Item ->
                    sum + item.amount
                }
        return categoryAndDraftSum == realSum
    }

    private fun populate(
        categoryItems: List<Item>,
        realItems: List<Item>,
        draftItems: List<Item>,
    ) {
        this.categoryItems = categoryItems
        this.realItems = realItems
        this.draftItems = draftItems
        require(validate()) { "attempt was made to create invalid transaction: $this" }
    }

    inner class Item(
        val amount: BigDecimal,
        val description: String? = null,
        val categoryAccount: CategoryAccount? = null,
        val realAccount: RealAccount? = null,
        val draftAccount: DraftAccount? = null,
        val draftStatus: DraftStatus = DraftStatus.none,
    ) {

        val transaction = this@Transaction

        override fun toString(): String =
            "TransactionItem(${categoryAccount ?: realAccount ?: draftAccount}, $amount${
                if (description?.isNotBlank() == true)
                    ", '$description'"
                else
                    ""
            })"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Item) return false

            if (transaction != other.transaction) return false
            if (amount != other.amount) return false
            if (description != other.description) return false
            if (categoryAccount != other.categoryAccount) return false
            if (realAccount != other.realAccount) return false
            if (draftAccount != other.draftAccount) return false
            if (draftStatus != other.draftStatus) return false

            return true
        }

        override fun hashCode(): Int {
            var result = amount.hashCode()
            result = 31 * result + (description?.hashCode() ?: 0)
            result = 31 * result + (categoryAccount?.hashCode() ?: 0)
            result = 31 * result + (realAccount?.hashCode() ?: 0)
            result = 31 * result + (draftAccount?.hashCode() ?: 0)
            result = 31 * result + draftStatus.hashCode()
            result = 31 * result + transaction.hashCode()
            return result
        }

    }

    class ItemBuilder(
        var amount: BigDecimal? = null,
        var description: String? = null,
        var categoryAccount: CategoryAccount? = null,
        var realAccount: RealAccount? = null,
        var draftAccount: DraftAccount? = null,
        var draftStatus: DraftStatus = DraftStatus.none,
    ) {
        fun build(transaction: Transaction): Item =
            transaction.Item(amount!!, description, categoryAccount, realAccount, draftAccount, draftStatus)

    }

    class Builder(
        var description: String? = null,
        var timestamp: Instant? = null,
        var id: UUID? = null,
        var clears: Transaction? = null,
    ) {
        val categoryItemBuilders: MutableList<ItemBuilder> = mutableListOf()
        val realItemBuilders: MutableList<ItemBuilder> = mutableListOf()
        val draftItemBuilders: MutableList<ItemBuilder> = mutableListOf()

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
                        .draftItemBuilders
                        .map { it.build(this) },
                )
            }

    }

}

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
