package bps.budget.transaction

import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

interface Transaction {
    val id: UUID
    val description: String

    // TODO consider BigDecimal with scale 2.  BigDecimal(string, 2)
    val amount: BigDecimal
    val timestamp: OffsetDateTime
    val categoryItems: List<TransactionItem>
    val realItems: List<TransactionItem>
    val draftItems: List<TransactionItem>

    fun validate(): Boolean {
        val categoryAndDraftSum: BigDecimal =
            (categoryItems + draftItems)
                .fold(BigDecimal.ZERO) { sum: BigDecimal, item: TransactionItem ->
                    sum + item.amount
                }
        val realSum: BigDecimal =
            realItems
                .fold(BigDecimal.ZERO) { sum: BigDecimal, item: TransactionItem ->
                    sum + item.amount
                }
        return categoryAndDraftSum == realSum
    }

    companion object {

        operator fun invoke(
            amount: BigDecimal,
            description: String,
            timestamp: OffsetDateTime = OffsetDateTime.now(),
            categoryItems: List<TransactionItem> = emptyList(),
            realItems: List<TransactionItem> = emptyList(),
            draftItems: List<TransactionItem> = emptyList(),
        ): Transaction = object : Transaction {
            override val id: UUID = UUID.randomUUID()
            override val description: String = description
            override val amount: BigDecimal = amount
            override val timestamp: OffsetDateTime = timestamp
            override val categoryItems: List<TransactionItem> = categoryItems
            override val realItems: List<TransactionItem> = realItems
            override val draftItems: List<TransactionItem> = draftItems
        }

    }
}

interface TransactionItem {
    val description: String
    val amount: BigDecimal
    val categoryAccount: CategoryAccount?
    val realAccount: RealAccount?
    val draftAccount: DraftAccount?

    fun commit() {

    }
}
