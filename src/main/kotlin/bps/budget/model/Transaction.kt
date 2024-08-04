package bps.budget.model

import java.math.BigDecimal
import java.math.RoundingMode
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
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: TransactionItem ->
                    sum + item.amount
                }
        val realSum: BigDecimal =
            realItems
                .fold(BigDecimal.ZERO.setScale(2)) { sum: BigDecimal, item: TransactionItem ->
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
            override val amount: BigDecimal = amount.setScale(2, RoundingMode.HALF_UP)
            override val timestamp: OffsetDateTime = timestamp
            override val categoryItems: List<TransactionItem> = categoryItems
            override val realItems: List<TransactionItem> = realItems
            override val draftItems: List<TransactionItem> = draftItems

            override fun toString(): String =
                buildString {
                    append("Transaction('$description', $amount")
                    (categoryItems + realItems + draftItems).forEach { transactionItem: TransactionItem ->
                        append(", $transactionItem")
                    }
                    append(")")
                }

        }

    }
}

