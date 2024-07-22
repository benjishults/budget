package bps.budget.persistence

import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import java.util.UUID

interface AccountConfig {
    val name: String
    val id: UUID
    val description: String
    val balance: Double
}

open class CategoryAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: Double = 0.0,
) : AccountConfig

fun CategoryAccountConfig.toCategoryAccount(): CategoryAccount =
    CategoryAccount(name, description, id, balance)

fun CategoryAccount.toConfig(): CategoryAccountConfig =
    CategoryAccountConfig(name, description, id, balance)

open class RealAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: Double = 0.0,
    val draftCompanionId: UUID? = null,
) : AccountConfig

fun RealAccountConfig.toRealAccount(draftCompanion: DraftAccount? = null): RealAccount =
    RealAccount(name, description, id, balance, draftCompanion)

fun RealAccount.toConfig(): RealAccountConfig =
    RealAccountConfig(name, description, id, balance, draftCompanion?.id)

open class DraftAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: Double = 0.0,
    val realCompanionId: UUID,
) : AccountConfig

fun DraftAccountConfig.toDraftAccount(realCompanion: RealAccount): DraftAccount =
    DraftAccount(name, description, id, balance, realCompanion)

fun DraftAccount.toConfig(): DraftAccountConfig =
    DraftAccountConfig(name, description, id, balance, realCompanion.id)
