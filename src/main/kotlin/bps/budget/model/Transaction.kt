package bps.budget.model

import bps.budget.persistence.AccountConfig

data class Transaction(
    val amountCents: Long,
    val from: AccountConfig,
    val to: AccountConfig,
)
