package bps.budget.model

import java.math.BigDecimal
import java.util.UUID

interface AccountData {
    val name: String
    val id: UUID
    val description: String
    val balance: BigDecimal
}
