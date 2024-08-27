package bps.budget.persistence.files

import bps.budget.model.AccountData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import java.math.BigDecimal
import java.util.UUID

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
open class CategoryAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : AccountData

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
fun CategoryAccountConfig.toCategoryAccount(): CategoryAccount =
    CategoryAccount(name, description, id, balance)

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
fun CategoryAccount.toConfig(): CategoryAccountConfig =
    CategoryAccountConfig(name, description, id, balance)

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
open class RealAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: BigDecimal = BigDecimal.ZERO.setScale(2),
) : AccountData

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
fun RealAccountConfig.toRealAccount(): RealAccount =
    RealAccount(name, description, id, balance)

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
fun RealAccount.toConfig(): RealAccountConfig =
    RealAccountConfig(name, description, id, balance)

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
open class DraftAccountConfig(
    override val name: String,
    override val description: String,
    override val id: UUID = UUID.randomUUID(),
    override val balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    val realCompanionId: UUID,
) : AccountData

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
fun DraftAccountConfig.toDraftAccount(realCompanion: RealAccount): DraftAccount =
    DraftAccount(name, description, id, balance, realCompanion)

@Deprecated(replaceWith = ReplaceWith("Use JDBC configuration"), message = "File configuration is no longer supported")
fun DraftAccount.toConfig(): DraftAccountConfig =
    DraftAccountConfig(name, description, id, balance, realCompanion.id)
