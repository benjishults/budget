package bps.budget.model

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class Transaction private constructor(
    val id: UUID,
    val description: String,
    val timestamp: OffsetDateTime,
) {

    lateinit var categoryItems: List<Item>
        private set
    lateinit var realItems: List<Item>
        private set
    lateinit var draftItems: List<Item>
        private set

    fun validate(): Boolean {
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

    @Volatile
    private var initialized: Boolean = false

    private fun populate(
        categoryItems: List<Item>,
        realItems: List<Item>,
        draftItems: List<Item>,
    ) {
        check(!initialized) { "attempt to initialize already-initialized transaction: $this" }
        this.categoryItems = categoryItems
        this.realItems = realItems
        this.draftItems = draftItems
        require(validate()) { "attempt was made to create invalid transaction: $this" }
        initialized = true
    }

    inner class Item(
        val amount: BigDecimal,
        val description: String? = null,
        val categoryAccount: CategoryAccount? = null,
        val realAccount: RealAccount? = null,
        val draftAccount: DraftAccount? = null,
    ) {

        lateinit var transaction: Transaction
            private set

        override fun toString(): String =
            "TransactionItem(${categoryAccount ?: realAccount ?: draftAccount}, $amount${
                if (description?.isNotBlank() == true)
                    ", '$description'"
                else
                    ""
            })"

    }

    class ItemBuilder(
        var amount: BigDecimal? = null,
        var description: String? = null,
        var categoryAccount: CategoryAccount? = null,
        var realAccount: RealAccount? = null,
        var draftAccount: DraftAccount? = null,
    ) {
        fun build(transaction: Transaction): Item =
            transaction.Item(amount!!, description, categoryAccount, realAccount, draftAccount)

    }

    class Builder(
        var description: String? = null,
        var timestamp: OffsetDateTime? = null,
        var id: UUID? = null,
    ) {
        val categoryItems: MutableList<ItemBuilder> = mutableListOf()
        val realItems: MutableList<ItemBuilder> = mutableListOf()
        val draftItems: MutableList<ItemBuilder> = mutableListOf()

        fun build(): Transaction = Transaction(
            this@Builder.id ?: UUID.randomUUID(),
            this@Builder.description!!,
            this@Builder.timestamp!!,
        )
            .apply {
                populate(
                    this@Builder.categoryItems.map { it.build(this) },
                    this@Builder.realItems.map { it.build(this) },
                    this@Builder.draftItems.map { it.build(this) },
                )
            }

    }

}
