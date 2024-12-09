package bps.budget.persistence.jdbc

import bps.budget.model.Account
import bps.budget.model.AccountType
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.model.defaultGeneralAccountDescription
import bps.budget.model.defaultGeneralAccountName
import bps.budget.persistence.AccountDao
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import bps.kotlin.Instrumentable
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

@Instrumentable
class JdbcAccountDao(
    val connection: Connection,
) : AccountDao, JdbcFixture {

    override fun <T : Account> getDeactivatedAccounts(
        type: String,
        budgetId: UUID,
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
    ): List<T> =
        connection.transactOrThrow {
            prepareStatement(
                """
    select acc.*
    from accounts acc
    where acc.type = ?
        and acc.budget_id = ?
        and not exists
            (select 1
             from account_active_periods aap
             where acc.id = aap.account_id
                 and acc.budget_id = aap.budget_id
                 and aap.end_date_utc > now()
                 and aap.start_date_utc < now()
            )
""".trimIndent(),
            )
                .use { getDeactivatedAccountsStatement ->
                    getDeactivatedAccountsStatement.setString(1, type)
                    getDeactivatedAccountsStatement.setUuid(2, budgetId)
                    getDeactivatedAccountsStatement
                        .executeQuery()
                        .use { resultSet: ResultSet ->
                            resultSet.extractAccounts(factory, budgetId)
                        }
                }
        }

    override fun <T : Account> getActiveAccounts(
        type: String,
        budgetId: UUID,
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
    ): List<T> =
        connection.prepareStatement(
            """
select acc.*
from accounts acc
         join account_active_periods aap
              on acc.id = aap.account_id
                  and acc.budget_id = aap.budget_id
where acc.budget_id = ?
  and acc.type = ?
  and now() > aap.start_date_utc
  and now() < aap.end_date_utc
""".trimIndent(),
        )
            .use { getAccounts: PreparedStatement ->
                getAccounts.setUuid(1, budgetId)
                getAccounts.setString(2, type)
                getAccounts.executeQuery()
                    .use { result ->
                        result.extractAccounts(factory, budgetId)
                    }
            }


    /**
     * Converts a [ResultSet] into list of [T]s
     * @param T the [Account] type
     */
    private fun <T : Account> ResultSet.extractAccounts(
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
        budgetId: UUID,
    ): List<T> =
        buildList {
            while (next()) {
                add(
                    factory(
                        getString("name"),
                        getString("description"),
                        getObject("id", UUID::class.java),
                        getCurrencyAmount("balance"),
                        budgetId,
                    ),
                )
            }
        }

    override fun deactivateAccount(account: Account) {
        connection.transactOrThrow {
            prepareStatement(
                """
update account_active_periods aap
set end_date_utc = now()
where aap.account_id = ?
  and now() > aap.start_date_utc
  and now() < aap.end_date_utc
                """.trimIndent(),
            )
                .use { deactivateActivityPeriod: PreparedStatement ->
                    deactivateActivityPeriod.setUuid(1, account.id)
                    deactivateActivityPeriod.executeUpdate()
                }
        }
    }

    override fun List<AccountDao.BalanceToAdd>.updateBalances(budgetId: UUID) =
        forEach { (accountId: UUID, amount: BigDecimal) ->
            connection.prepareStatement(
                """
                        update accounts
                        set balance = balance + ?
                        where id = ? and budget_id = ?""".trimIndent(),
            )
                .use { preparedStatement: PreparedStatement ->
                    preparedStatement.setBigDecimal(1, amount)
                    preparedStatement.setUuid(2, accountId)
                    preparedStatement.setUuid(3, budgetId)
                    preparedStatement.executeUpdate()
                }
        }

    override fun updateAccount(account: Account): Boolean =
        connection.transactOrThrow {
            prepareStatement(
                """
                update accounts
                set name = ?,
                    description = ?
                where id = ?
                    and budget_id = ?
            """.trimIndent(),
            )
                .use { preparedStatement: PreparedStatement ->
                    preparedStatement.setString(1, account.name)
                    preparedStatement.setString(2, account.description)
                    preparedStatement.setUuid(3, account.id)
                    preparedStatement.setUuid(4, account.budgetId)
                    preparedStatement.executeUpdate() == 1
                }
        }

    override fun createCategoryAccountOrNull(name: String, description: String, budgetId: UUID): CategoryAccount? =
        connection.transactOrThrow {
            prepareStatement(
                """
                insert into accounts (name, description, balance, type, budget_id, id)
                values (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            )
                .use { preparedStatement: PreparedStatement ->
                    val id = preparedStatement.setAccountParametersAndReturnId(
                        name,
                        description,
                        AccountType.category,
                        budgetId,
                    )
                    if (preparedStatement.executeUpdate() == 1) {
                        CategoryAccount(
                            name = name,
                            description = description,
                            id = id,
                            balance = BigDecimal.ZERO.setScale(2),
                            budgetId = budgetId,
                        )
                    } else
                        null
                }
                ?.also { insertAccountActivePeriod(it, budgetId) }
        }

    private fun Connection.insertAccountActivePeriod(account: Account, budgetId: UUID) =
        prepareStatement(
            """
                            insert into account_active_periods (id, account_id, budget_id)
                            values (?, ?, ?)
                            on conflict do nothing
                        """.trimIndent(),
        )
            .use { createActivePeriod: PreparedStatement ->
                createActivePeriod.setUuid(1, UUID.randomUUID())
                createActivePeriod.setUuid(2, account.id)
                createActivePeriod.setUuid(3, budgetId)
                // NOTE due to the uniqueness constraints on this table, this will be idempotent
                createActivePeriod.executeUpdate()
            }

    override fun createGeneralAccountWithIdOrNull(id: UUID, balance: BigDecimal, budgetId: UUID): CategoryAccount? =
        connection.transactOrThrow {
            prepareStatement(
                """
                insert into accounts (name, description, balance, type, budget_id, id)
                values (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            )
                .use { preparedStatement: PreparedStatement ->
                    preparedStatement.setString(1, defaultGeneralAccountName)
                    preparedStatement.setString(2, defaultGeneralAccountDescription)
                    preparedStatement.setBigDecimal(3, balance)
                    preparedStatement.setString(4, AccountType.category.name)
                    preparedStatement.setUuid(5, budgetId)
                    preparedStatement.setUuid(6, id)
                    if (preparedStatement.executeUpdate() == 1) {
                        CategoryAccount(
                            name = defaultGeneralAccountName,
                            description = defaultGeneralAccountDescription,
                            id = id,
                            balance = balance,
                            budgetId = budgetId,
                        )
                    } else
                        null
                }
                ?.also { insertAccountActivePeriod(it, budgetId) }
        }

    override fun createRealAccountOrNull(
        name: String,
        description: String,
        budgetId: UUID,
        balance: BigDecimal,
    ): RealAccount? =
        connection.transactOrThrow {
            createRealAccountInTransaction(name, description, budgetId, balance)
        }

    private fun Connection.createRealAccountInTransaction(
        name: String,
        description: String,
        budgetId: UUID,
        balance: BigDecimal,
    ): RealAccount? =
        prepareStatement(
            """
                    insert into accounts (name, description, balance, type, budget_id, id)
                    values (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
        )
            .use { preparedStatement: PreparedStatement ->
                val id = preparedStatement.setAccountParametersAndReturnId(
                    name,
                    description,
                    AccountType.real,
                    budgetId,
                    balance,
                )
                if (preparedStatement.executeUpdate() == 1) {
                    RealAccount(
                        name = name,
                        description = description,
                        id = id,
                        balance = balance,
                        budgetId = budgetId,
                    )
                } else
                    null
            }
            ?.also { insertAccountActivePeriod(it, budgetId) }

    override fun createRealAndDraftAccountOrNull(
        name: String,
        description: String,
        budgetId: UUID,
        balance: BigDecimal,
    ): Pair<RealAccount, DraftAccount>? =
        connection.transactOrThrow {
            createRealAccountInTransaction(name, description, budgetId, balance)
                ?.let { realCompanion: RealAccount ->
                    prepareStatement(
                        """
                            insert into accounts (name, description, balance, type, budget_id, id, companion_account_id)
                            values (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                    )
                        .use { createDraftAccountStatement: PreparedStatement ->
                            val id = createDraftAccountStatement.setAccountParametersAndReturnId(
                                name,
                                description,
                                AccountType.draft,
                                budgetId,
                            )
                            createDraftAccountStatement.setUuid(7, realCompanion.id)
                            if (createDraftAccountStatement.executeUpdate() == 1) {
                                realCompanion to DraftAccount(
                                    name = name,
                                    description = description,
                                    id = id,
                                    balance = BigDecimal.ZERO.setScale(2),
                                    budgetId = budgetId,
                                    realCompanion = realCompanion,
                                )
                            } else
                                null
                        }
                        ?.also { (_, draftAccount) -> insertAccountActivePeriod(draftAccount, budgetId) }
                }
        }

    override fun createChargeAccountOrNull(
        name: String,
        description: String,
        budgetId: UUID,
    ): ChargeAccount? =
        connection.transactOrThrow {
            prepareStatement(
                """
                insert into accounts (name, description, balance, type, budget_id, id)
                values (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            )
                .use { preparedStatement: PreparedStatement ->
                    val id = preparedStatement.setAccountParametersAndReturnId(
                        name,
                        description,
                        AccountType.charge,
                        budgetId,
                    )
                    if (preparedStatement.executeUpdate() == 1) {
                        ChargeAccount(
                            name = name,
                            description = description,
                            id = id,
                            balance = BigDecimal.ZERO.setScale(2),
                            budgetId = budgetId,
                        )
                    } else
                        null
                }
                ?.also { insertAccountActivePeriod(it, budgetId) }
        }

    private fun PreparedStatement.setAccountParametersAndReturnId(
        name: String,
        description: String,
        type: AccountType,
        budgetId: UUID,
        balance: BigDecimal = BigDecimal.ZERO.setScale(2),
    ): UUID =
        UUID.randomUUID()
            .also {
                setString(1, name)
                setString(2, description)
                setBigDecimal(3, balance)
                setString(4, type.name)
                setUuid(5, budgetId)
                setUuid(6, it)
            }

}
