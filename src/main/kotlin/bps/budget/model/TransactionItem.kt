package bps.budget.model

import java.math.BigDecimal

interface TransactionItem {

    val description: String?
    val amount: BigDecimal
    val categoryAccount: CategoryAccount?
    val realAccount: RealAccount?
    val draftAccount: DraftAccount?

    companion object {
        operator fun invoke(
            amount: BigDecimal,
            description: String? = null,
            categoryAccount: CategoryAccount? = null,
            realAccount: RealAccount? = null,
            draftAccount: DraftAccount? = null,
        ): TransactionItem = object : TransactionItem {
            override val description: String? = description
            override val amount: BigDecimal = amount
            override val categoryAccount: CategoryAccount? = categoryAccount
            override val realAccount: RealAccount? = realAccount
            override val draftAccount: DraftAccount? = draftAccount
            override fun toString(): String =
                "TransactionItem(${categoryAccount ?: realAccount ?: draftAccount}, $amount${
                    if (description?.isNotBlank() == true)
                        ", '$description'"
                    else
                        ""
                })"
        }
    }

}
