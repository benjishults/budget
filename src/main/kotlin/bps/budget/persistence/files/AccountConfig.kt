package bps.budget.persistence.files

import bps.budget.model.AccountData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import java.math.BigDecimal
import java.util.UUID

open class CategoryAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: BigDecimal = BigDecimal.ZERO,
) : AccountData

fun CategoryAccountConfig.toCategoryAccount(): CategoryAccount =
    CategoryAccount(name, description, id, balance)

fun CategoryAccount.toConfig(): CategoryAccountConfig =
    CategoryAccountConfig(name, description, id, balance)

open class RealAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: BigDecimal = BigDecimal.ZERO,
) : AccountData

fun RealAccountConfig.toRealAccount(): RealAccount =
    RealAccount(name, description, id, balance)

fun RealAccount.toConfig(): RealAccountConfig =
    RealAccountConfig(name, description, id, balance)

open class DraftAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: BigDecimal = BigDecimal.ZERO,
    val realCompanionId: UUID,
) : AccountData

fun DraftAccountConfig.toDraftAccount(realCompanion: RealAccount): DraftAccount =
    DraftAccount(name, description, id, balance, realCompanion)

fun DraftAccount.toConfig(): DraftAccountConfig =
    DraftAccountConfig(name, description, id, balance, realCompanion.id)
