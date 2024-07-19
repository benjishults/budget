package bps.budget.persistence

import java.util.UUID

sealed interface AccountConfig {
    val name: String
    val id: UUID
    val description: String
    val balance: Double
}

data class CategoryAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: Double = 0.0,
) : AccountConfig

data class RealAccountConfig(
    override val name: String,
    override val description: String,
    val draftCompanionId: UUID,
    override val id: UUID = UUID.randomUUID(),
    override val balance: Double = 0.0,
) : AccountConfig

data class DraftAccountConfig(
    override val name: String,
    override val description: String,
    val realCompanionId: UUID,
    override val id: UUID = UUID.randomUUID(),
    override val balance: Double = 0.0,
) : AccountConfig
