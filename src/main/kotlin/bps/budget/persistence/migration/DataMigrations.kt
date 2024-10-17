package bps.budget.persistence.migration

import bps.budget.BudgetConfigurations
import bps.budget.model.Account
import bps.budget.persistence.jdbc.JdbcDao
import bps.config.convertToPath
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrThrow
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Types.OTHER
import java.sql.Types.VARCHAR
import java.util.UUID

class DataMigrations {

    companion object : JdbcFixture {
        @JvmStatic
        fun main(args: Array<String>) {
            val argsList: List<String> = args.toList()
            val typeIndex = argsList.indexOfFirst { it == "-type" } + 1
            if ("-type" !in argsList || argsList.size <= typeIndex) {
                println("Usage: java DataMigrations -type <type> [-schema <schema>]")
                println("Migration types: new-account_active_periods-table, move-to-single-account-table")
            } else {
                val configurations =
                    BudgetConfigurations(
                        sequenceOf(
                            "migrations.yml",
                            convertToPath("~/.config/bps-budget/migrations.yml"),
                        ),
                    )
                val jdbcDao = JdbcDao(configurations.persistence.jdbc!!)
                val migrationType = argsList[typeIndex]
                with(jdbcDao.connection) {
                    when (migrationType) {
                        "move-to-single-account-table" -> {
                            jdbcDao.use {
                                transactOrThrow {
                                    prepareStatement(
                                        """
create table if not exists accounts
(
    id                   uuid           not null unique,
    name                 varchar(50)    not null,
    type                 varchar(20)    not null,
    description          varchar(110)   not null default '',
    balance              numeric(30, 2) not null default 0.0,
    companion_account_id uuid           null references accounts (id),
    budget_id            uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, type, budget_id),
    unique (companion_account_id, budget_id)
)
                                    """.trimIndent(),
                                    )
                                        .use { statement ->
                                            statement.execute()
                                        }
                                    prepareStatement(
                                        """
create index if not exists accounts_by_type
    on accounts (budget_id, type)
                                    """.trimIndent(),
                                    )
                                        .use { statement ->
                                            statement.execute()
                                        }
                                    migrateAccountsToSingleTable("category")
                                    migrateAccountsToSingleTable("real")
                                    migrateAccountsToSingleTable("charge")
                                    migrateAccountsToSingleTable("draft")
                                    migrateActivePeriods()
                                    migrateTransactions()
                                }
                            }
                        }
                        "new-account_active_periods-table" ->
                            jdbcDao.use {
                                try {
                                    transactOrThrow {
                                        prepareStatement(
                                            """
                            select ba.budget_id
                            from users u
                            join budget_access ba on u.id = ba.user_id
                            where ba.budget_name = ?
                            and u.login = ?
                            """.trimIndent(),
                                        )
                                            .use { statement ->
                                                statement.setString(1, configurations.persistence.jdbc!!.budgetName)
                                                statement.setString(2, configurations.user.defaultLogin)
                                                statement.executeQuery().use { resultSet ->
                                                    resultSet.next()
                                                    val budgetId: UUID = resultSet.getUuid("budget_id")!!
                                                    migrateAccountsToActivityPeriodTable("category", budgetId)
                                                    migrateAccountsToActivityPeriodTable("real", budgetId)
                                                    migrateAccountsToActivityPeriodTable("charge", budgetId)
                                                    migrateAccountsToActivityPeriodTable("draft", budgetId)
                                                }
                                            }
                                    }
                                } catch (ex: Throwable) {
                                    ex.printStackTrace()
                                }
                            }
                        else -> throw IllegalArgumentException("Unknown migration type: $migrationType")
                    }
                }
            }
        }

        private fun Connection.migrateActivePeriods() {
            data class ActivePeriod(
                val id: UUID,
                val budgetId: UUID,
                val startDateUtc: Instant,
                val endDateUtc: Instant,
                val categoryAccountId: UUID?,
                val draftAccountId: UUID?,
                val chargeAccountId: UUID?,
                val realAccountId: UUID?,
            )
            buildList {
                prepareStatement("select * from account_active_periods")
                    .use { statement ->
                        statement.executeQuery()
                            .use { resultSet ->
                                while (resultSet.next()) {
                                    add(
                                        ActivePeriod(
                                            id = resultSet.getUuid("id")!!,
                                            budgetId = resultSet.getUuid("budget_id")!!,
                                            startDateUtc = resultSet.getInstant("start_date_utc"),
                                            endDateUtc = resultSet.getInstant("end_date_utc"),
                                            categoryAccountId = resultSet.getUuid("category_account_id"),
                                            draftAccountId = resultSet.getUuid("draft_account_id"),
                                            chargeAccountId = resultSet.getUuid("charge_account_id"),
                                            realAccountId = resultSet.getUuid("real_account_id"),
                                        ),
                                    )
                                }
                            }
                    }
            }
                .let { activePeriods ->
                    // TODO delete table and recreate with new rows
                    prepareStatement(
                        """
                    create table if not exists account_active_periods_temp
                    (
                        id             uuid      not null unique,
                        start_date_utc timestamp not null default '0001-01-01T00:00:00Z',
                        end_date_utc   timestamp not null default '9999-12-31T23:59:59.999Z',
                        account_id     uuid      not null references accounts (id),
                        budget_id      uuid      not null references budgets (id),
                        unique (start_date_utc, account_id, budget_id)
                    )
                """.trimIndent(),
                    )
                        .use {
                            it.executeUpdate()
                        }
                    prepareStatement(
                        """
create index if not exists lookup_account_active_periods_by_account_id
    on account_active_periods_temp (account_id, budget_id)
                        """.trimIndent(),
                    )
                        .use {
                            it.executeUpdate()
                        }
                    // TODO copy this data into it
                    activePeriods.forEach { activePeriod: ActivePeriod ->
                        prepareStatement(
                            """
                            insert into account_active_periods_temp (id, start_date_utc, end_date_utc, account_id, budget_id)
                            values (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        )
                            .use { statement ->
                                statement.setUuid(1, activePeriod.id)
                                statement.setTimestamp(2, activePeriod.startDateUtc)
                                statement.setTimestamp(3, activePeriod.endDateUtc)
                                statement.setUuid(
                                    4,
                                    (activePeriod.realAccountId ?: activePeriod.draftAccountId
                                    ?: activePeriod.chargeAccountId ?: activePeriod.categoryAccountId)!!,
                                )
                                statement.setUuid(5, activePeriod.budgetId)
                                statement.executeUpdate()
                            }
                    }
                    prepareStatement("drop table if exists account_active_periods")
                        .use { statement ->
                            statement.execute()
                        }
                    prepareStatement("alter table if exists account_active_periods_temp rename to account_active_periods")
                        .use { statement ->
                            statement.execute()
                        }
                }
        }

        private fun Connection.migrateTransactions() {
            data class TransactionItem(
                val transactionId: UUID,
                val budgetId: UUID,
                val description: String?,
                val amount: BigDecimal,
                val draftStatus: String?,
                val categoryAccountId: UUID?,
                val draftAccountId: UUID?,
                val chargeAccountId: UUID?,
                val realAccountId: UUID?,
            )
            buildList {
                prepareStatement("select * from transaction_items")
                    .use { statement ->
                        statement.executeQuery()
                            .use { resultSet ->
                                while (resultSet.next()) {
                                    add(
                                        TransactionItem(
                                            transactionId = resultSet.getUuid("transaction_id")!!,
                                            budgetId = resultSet.getUuid("budget_id")!!,
                                            description = resultSet.getString("description"),
                                            amount = resultSet.getCurrencyAmount("amount"),
                                            draftStatus = resultSet.getString("draft_status"),
                                            categoryAccountId = resultSet.getUuid("category_account_id"),
                                            draftAccountId = resultSet.getUuid("draft_account_id"),
                                            chargeAccountId = resultSet.getUuid("charge_account_id"),
                                            realAccountId = resultSet.getUuid("real_account_id"),
                                        ),
                                    )
                                }
                            }
                    }
            }
                .let { transactionItems ->
                    // TODO delete table and recreate with new rows
                    prepareStatement(
                        """
create table if not exists transaction_items_temp
(
    transaction_id      uuid           not null references transactions (id),
    description         varchar(110)   null,
    amount              numeric(30, 2) not null,
    account_id uuid          not null references accounts (id),
    draft_status        varchar        not null default 'none', -- 'none' 'outstanding' 'cleared'
    budget_id           uuid           not null references budgets (id)
)
                """.trimIndent(),
                    )
                        .use {
                            it.executeUpdate()
                        }
                    prepareStatement(
                        """
create index if not exists lookup_account_transaction_items_by_account
    on transaction_items_temp (account_id, budget_id)
                        """.trimIndent(),
                    )
                        .use { it.executeUpdate() }
                    // TODO copy this data into it
                    transactionItems.forEach { transactionItem: TransactionItem ->
                        prepareStatement(
                            """
                            insert into transaction_items_temp (transaction_id, description, amount, account_id, draft_status, budget_id)
                            values (?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        )
                            .use { statement ->
                                statement.setUuid(1, transactionItem.transactionId)
                                transactionItem.description
                                    ?.let { statement.setString(2, it) }
                                    ?: statement.setNull(2, VARCHAR)
                                statement.setBigDecimal(3, transactionItem.amount)
                                statement.setUuid(
                                    4,
                                    (transactionItem.realAccountId
                                        ?: transactionItem.draftAccountId
                                        ?: transactionItem.chargeAccountId
                                        ?: transactionItem.categoryAccountId)!!,
                                )
                                transactionItem.draftStatus
                                    ?.let { statement.setString(5, it) }
                                    ?: statement.setNull(5, VARCHAR)
                                statement.setUuid(6, transactionItem.budgetId)
                                statement.executeUpdate()
                            }
                    }
                    prepareStatement("drop table if exists transaction_items")
                        .use {
                            it.executeUpdate()
                        }
                    prepareStatement("alter table if exists transaction_items_temp rename to transaction_items")
                        .use {
                            it.executeUpdate()
                        }
                }
        }

        private fun Connection.migrateAccountsToSingleTable(type: String) {
            buildList {
                prepareStatement("select * from ${type}_accounts")
                    .use { statement ->
                        statement.executeQuery().use { resultSet ->
                            while (resultSet.next()) {
                                add(
                                    Account(
                                        name = resultSet.getString("name"),
                                        description = resultSet.getString("description"),
                                        id = resultSet.getUuid("id")!!,
                                        balance = resultSet.getCurrencyAmount("balance"),
                                    )
                                            to resultSet.getUuid("budget_id")!!
                                            to
                                            if (type == "draft")
                                                resultSet.getUuid("real_account_id")
                                            else
                                                null,
                                )
                            }
                        }
                    }
            }
                .forEach { (pair, companionId) ->
                    val (account, budgetId) = pair
                    prepareStatement(
                        """
                |insert into accounts (id, name, description, type, companion_account_id, budget_id, balance)
                |VALUES (?, ?, ?, ?, ?, ?, ?)
                |on conflict do nothing
                """.trimMargin(),
                    )
                        .use { statement ->
                            statement.setUuid(1, account.id)
                            statement.setString(2, account.name)
                            statement.setString(3, account.description)
                            statement.setString(4, type)
                            companionId
                                ?.let {
                                    statement.setUuid(5, it)
                                }
                                ?: statement.setNull(5, OTHER)
                            statement.setUuid(6, budgetId)
                            statement.setBigDecimal(7, account.balance)
                            statement.executeUpdate()
                        }
                }
        }

        private fun Connection.migrateAccountsToActivityPeriodTable(tablePrefix: String, budgetId: UUID) {
            buildList {
                prepareStatement("select id from ${tablePrefix}_accounts where budget_id = ?")
                    .use { statement ->
                        statement.setUuid(1, budgetId)
                        statement.executeQuery()
                            .use { resultSet ->
                                while (resultSet.next()) {
                                    add(resultSet.getUuid("id")!!)
                                }
                            }
                    }
            }
                .let { accountIds: List<UUID> ->
                    accountIds.forEach { accountId ->
                        prepareStatement(
                            """
                    insert into account_active_periods (id, ${tablePrefix}_account_id, budget_id)
                    values (?, ?, ?)
                    on conflict do nothing
                        """.trimIndent(),
                        )
                            .use { statement ->
                                statement.setUuid(1, UUID.randomUUID())
                                statement.setUuid(2, accountId)
                                statement.setUuid(3, budgetId)
                                statement.executeUpdate()
                            }

                    }
                }
        }
    }

}

