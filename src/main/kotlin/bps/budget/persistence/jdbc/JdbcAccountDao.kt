package bps.budget.persistence.jdbc

import bps.budget.model.Account
import bps.budget.model.Transaction
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
    errorStateTracker: Any,
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


    private fun <T : Account> ResultSet.extractAccounts(
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
        budgetId: UUID,
    ) =
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

    override fun updateBalances(transaction: Transaction, budgetId: UUID) {
        buildList {
            transaction.allItems().forEach { transactionItem: Transaction.Item<*> ->
                add(transactionItem.account.id to transactionItem.amount)
            }
        }
            .forEach { (accountId: UUID, amount: BigDecimal) ->
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
    }

}
