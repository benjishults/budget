package bps.budget.persistence.jdbc

import bps.budget.model.Account
import bps.budget.model.AccountType
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.persistence.AccountDao
import bps.budget.persistence.AnalyticsDao
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.TransactionDao
import bps.budget.persistence.UserBudgetDao
import bps.jdbc.JdbcFixture
import bps.jdbc.transact
import bps.jdbc.transactOrThrow
import bps.kotlin.Instrumentable
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.net.URLEncoder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Instrumentable
class JdbcDao(
    val config: JdbcConfig,
) : BudgetDao, JdbcFixture {

    private var jdbcURL: String =
        "jdbc:${config.dbProvider}://${config.host}:${config.port}/${
            URLEncoder.encode(
                config.database,
                "utf-8",
            )
        }?currentSchema=${URLEncoder.encode(config.schema, "utf-8")}"

    final var connection: Connection = startConnection()
        private set
    private val keepAliveSingleThreadScheduledExecutor = Executors
        .newSingleThreadScheduledExecutor()

    inner class ErrorStateTracker {
        // NOTE be sure to make this volatile if we go multithreaded
        var errorState: Throwable? = null
            private set

        fun <T> catchCommitErrorState(block: () -> T): T =
            try {
                block()
            } catch (e: Throwable) {
                errorState = e
                throw e
            }
    }

    /**
     * This will probably not be needed as we stop depending on the [save] method.  This is checked there so that, on
     * close, we don't try to save bad data.
     */
    private val errorStateTracker = ErrorStateTracker()

    override val userBudgetDao: UserBudgetDao = JdbcUserBudgetDao(connection, errorStateTracker)
    override val accountDao: AccountDao = JdbcAccountDao(connection)
    override val transactionDao: TransactionDao = JdbcTransactionDao(connection, errorStateTracker, accountDao)
    override val analyticsDao: AnalyticsDao = JdbcAnalyticsDao(connection, accountDao)

    init {
        // NOTE keep the connection alive with an occasional call to `isValid`.
        keepAliveSingleThreadScheduledExecutor
            .apply {
                scheduleWithFixedDelay(
                    {
                        if (!connection.isValid(4_000)) {
                            // TODO log this
                            connection = startConnection()
                        }
                    },
                    5_000,
                    20_000,
                    TimeUnit.SECONDS,
                )

            }
    }

    private fun startConnection(): Connection =
        DriverManager
            .getConnection(
                jdbcURL,
                config.user ?: System.getenv("BUDGET_JDBC_USER"),
                config.password ?: System.getenv("BUDGET_JDBC_PASSWORD"),
            )
            .apply {
                autoCommit = false
            }

    override fun prepForFirstLoad() {
        connection.transactOrThrow {
            createStatement()
                .use { createStatement: Statement ->
                    createStatement.executeUpdate(
                        """
create table if not exists users
(
    id    uuid         not null primary key,
    login varchar(110) not null unique
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists budgets
(
    id                 uuid not null primary key,
    general_account_id uuid not null unique
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists budget_access
(
    id              uuid         not null primary key,
    user_id         uuid         not null references users (id),
    budget_id       uuid         not null references budgets (id),
    budget_name     varchar(110) not null,
    time_zone       varchar(110) not null,
    analytics_start timestamp    not null default now(),
    -- if null, check fine_access
    coarse_access   varchar,
    unique (user_id, budget_id),
    unique (user_id, budget_name)
)                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_budget_access_by_user
    on budget_access (user_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
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
                    createStatement.executeUpdate(
                        """
create table if not exists account_active_periods
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
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_account_active_periods_by_account_id
    on account_active_periods (account_id, budget_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists transactions
(
    id                        uuid         not null unique,
    description               varchar(110) not null default '',
    timestamp_utc             timestamp    not null default now(),
    type                      varchar(20)  not null,
    -- the transaction that clears this transaction
    cleared_by_transaction_id uuid         null,
    budget_id                 uuid         not null references budgets (id),
    primary key (id, budget_id)
)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_transaction_by_date
    on transactions (timestamp_utc desc, budget_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_transaction_by_type_and_date
    on transactions (timestamp_utc desc, type, budget_id)
                    """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create table if not exists transaction_items
(
    id             uuid           not null unique,
    transaction_id uuid           not null references transactions (id),
    description    varchar(110)   null,
    amount         numeric(30, 2) not null,
    account_id     uuid           not null references accounts (id),
    draft_status   varchar        not null default 'none', -- 'none' 'outstanding' 'cleared'
    budget_id      uuid           not null references budgets (id)
)
                """.trimIndent(),
                    )
                    createStatement.executeUpdate(
                        """
create index if not exists lookup_transaction_items_by_account
    on transaction_items (account_id, budget_id)
                    """.trimIndent(),
                    )
                }
        }
    }

    private fun Connection.createStagingAccountsTable() {
        createStatement().use { createTablesStatement: Statement ->
            // TODO needed?
            createTablesStatement.executeUpdate("drop table if exists staged_accounts")
            createTablesStatement.executeUpdate(
                """
    create temp table if not exists staged_accounts
    (
        id                   uuid           not null,
        gen                  integer        not null generated always as identity,
        name                 varchar(50)    not null,
        description          varchar(110)   not null default '',
        balance              numeric(30, 2) not null default 0.0,
        companion_account_id uuid           null,
        budget_id            uuid           not null
    )
        on commit drop
                        """.trimIndent(),
            )
        }
    }

    private data class BudgetDataInfo(
        val generalAccountId: UUID,
        val timeZone: TimeZone,
        val analyticsStart: Instant,
        val budgetName: String,
    )

    /**
     * Just loads top-level account info.  Details of transactions are loaded on-demand.
     * @throws DataConfigurationException if data isn't found.
     */
    override fun load(budgetId: UUID, userId: UUID): BudgetData =
        try {
            connection.transact(
                onRollback = { ex ->
                    throw DataConfigurationException(ex.message, ex)
                },
            ) {
                val (generalAccountId: UUID, timeZone: TimeZone, analyticsStart: Instant, budgetName: String) =
                    prepareStatement(
                        """
                            select b.general_account_id, ba.time_zone, ba.budget_name, ba.analytics_start
                            from budgets b
                                join budget_access ba on b.id = ba.budget_id
                                join users u on u.id = ba.user_id
                            where b.id = ?
                                and u.id = ?
                        """.trimIndent(),
                    )
                        .use { getBudget: PreparedStatement ->
                            getBudget.setUuid(1, budgetId)
                            getBudget.setUuid(2, userId)
                            getBudget.executeQuery()
                                .use { result: ResultSet ->
                                    if (result.next()) {
                                        BudgetDataInfo(
                                            generalAccountId = result.getObject("general_account_id", UUID::class.java),
                                            timeZone = result.getString("time_zone")
                                                ?.let { timeZone -> TimeZone.of(timeZone) }
                                                ?: TimeZone.currentSystemDefault(),
                                            analyticsStart = result.getInstant("analytics_start"),
                                            budgetName = result.getString("budget_name"),
                                        )
                                    } else
                                        throw DataConfigurationException("Budget data not found for name: ${config.budgetName}")
                                }
                        }
                // TODO pull out duplicate code in these next three sections
                val categoryAccounts: List<CategoryAccount> =
                    accountDao.getActiveAccounts(AccountType.category.name, budgetId, ::CategoryAccount)
                val generalAccount: CategoryAccount =
                    categoryAccounts.find {
                        it.id == generalAccountId
                    }!!
                val realAccounts: List<RealAccount> =
                    accountDao.getActiveAccounts(AccountType.real.name, budgetId, ::RealAccount)
                val chargeAccounts: List<ChargeAccount> =
                    accountDao.getActiveAccounts(AccountType.charge.name, budgetId, ::ChargeAccount)
                val draftAccounts: List<DraftAccount> = // getAccounts("draft", budgetId, ::DraftAccount)
                    prepareStatement(
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
                        .use { getDraftAccountsStatement ->
                            getDraftAccountsStatement.setUuid(1, budgetId)
                            getDraftAccountsStatement.setString(2, AccountType.draft.name)
                            getDraftAccountsStatement.executeQuery()
                                .use { result ->
                                    buildList {
                                        while (result.next()) {
                                            add(
                                                DraftAccount(
                                                    result.getString("name"),
                                                    result.getString("description"),
                                                    result.getObject("id", UUID::class.java),
                                                    result.getCurrencyAmount("balance"),
                                                    realAccounts.find {
                                                        it.id.toString() == result.getString("companion_account_id")
                                                    }!!,
                                                    budgetId,
                                                ),
                                            )
                                        }
                                    }
                                }
                        }
                BudgetData(
                    budgetId,
                    budgetName,
                    timeZone,
                    analyticsStart,
                    generalAccount,
                    categoryAccounts,
                    realAccounts,
                    chargeAccounts,
                    draftAccounts,
                )
            }
        } catch (ex: Exception) {
            if (ex is DataConfigurationException) {
                throw ex
            } else
                throw DataConfigurationException(ex)
        }

//    /**
//     * Saves only if we have not had an error trying to commit.
//     */
//    // FIXME figure out what this should really be doing and what should be done elsewhere.
//    override fun save(budgetData: BudgetData, authenticatedUser: AuthenticatedUser) {
//        require(budgetData.validate())
//        errorStateTracker.errorState
//            ?: connection.transactOrThrow {
//                val generalAccountId: UUID = budgetData.generalAccount.id
//                // create user and budget if it isn't there
//                prepareStatement(
//                    """
//                insert into users (id, login)
//                values (?, ?) on conflict do nothing""",
//                )
//                    .use { createBudgetStatement: PreparedStatement ->
//                        createBudgetStatement.setUuid(1, authenticatedUser.id)
//                        createBudgetStatement.setString(2, authenticatedUser.login)
//                        createBudgetStatement.executeUpdate()
//                    }
//                prepareStatement(
//                    """
//                insert into budgets (id, general_account_id)
//                values (?, ?) on conflict do nothing""",
//                )
//                    .use { createBudgetStatement: PreparedStatement ->
//                        createBudgetStatement.setUuid(1, budgetData.id)
//                        createBudgetStatement.setUuid(2, generalAccountId)
//                        createBudgetStatement.executeUpdate()
//                    }
//                prepareStatement(
//                    """
//                        insert into budget_access (id, budget_id, user_id, time_zone, budget_name)
//                        values (?, ?, ?, ?, ?) on conflict do nothing""".trimIndent(),
//                )
//                    .use { createBudgetStatement: PreparedStatement ->
//                        createBudgetStatement.setUuid(1, UUID.randomUUID())
//                        createBudgetStatement.setUuid(2, budgetData.id)
//                        createBudgetStatement.setUuid(3, authenticatedUser.id)
//                        createBudgetStatement.setString(4, budgetData.timeZone.id)
//                        createBudgetStatement.setString(5, config.budgetName)
//                        createBudgetStatement.executeUpdate()
//                    }
//                // create accounts that aren't there and update those that are
//                createStagingAccountsTable()
//                upsertAccountData(budgetData.categoryAccounts, AccountType.category.name, budgetData.id)
//                createStagingAccountsTable()
//                upsertAccountData(budgetData.realAccounts, AccountType.real.name, budgetData.id)
//                createStagingAccountsTable()
//                upsertAccountData(budgetData.chargeAccounts, AccountType.charge.name, budgetData.id)
//                createStagingAccountsTable()
//                upsertAccountData(budgetData.draftAccounts, AccountType.draft.name, budgetData.id)
//            }
//    }

    /**
     * Must be called within a transaction with manual commits
     */
    // TODO we want to be in a state where we don't need to call this!
    private fun Connection.upsertAccountData(
        accounts: List<Account>,
        accountType: String,
        budgetId: UUID,
    ) {
        accounts.forEach { account ->
            // upsert account
            prepareStatement(
                """
                insert into staged_accounts (id, name, description, balance,${if (accountType == "draft") "companion_account_id, " else ""} budget_id)
                VALUES (?, ?, ?, ?,${if (accountType == "draft") " ?," else ""} ?)
                on conflict do nothing
            """.trimIndent(),
            )
                .use { createStagedAccountStatement: PreparedStatement ->
                    var parameterIndex = 1
                    createStagedAccountStatement.setUuid(parameterIndex++, account.id)
                    createStagedAccountStatement.setString(parameterIndex++, account.name)
                    createStagedAccountStatement.setString(parameterIndex++, account.description)
                    createStagedAccountStatement.setBigDecimal(parameterIndex++, account.balance)
                    if (accountType == "draft") {
                        createStagedAccountStatement.setUuid(
                            parameterIndex++,
                            (account as DraftAccount).realCompanion.id,
                        )
                    }
                    createStagedAccountStatement.setUuid(parameterIndex++, budgetId)
                    createStagedAccountStatement.executeUpdate()
                }
            prepareStatement(
                """
                merge into accounts as t
                    using staged_accounts as s
                    on (t.id = s.id or t.name = s.name) and t.budget_id = s.budget_id
                    when matched then
                        update
                        set name = s.name,
                            description = s.description,
                            balance = s.balance
                    when not matched then
                        insert (id, name, description, balance, type, ${if (accountType == "draft") "companion_account_id, " else ""} budget_id)
                        values (s.id, s.name, s.description, s.balance, ?, ${if (accountType == "draft") "s.companion_account_id, " else ""} s.budget_id);
                """.trimIndent(),
            )
                .use { createAccountStatement: PreparedStatement ->
                    createAccountStatement.setString(1, accountType)
                    createAccountStatement.executeUpdate()
                }
            // upsert account_active_periods entry
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
        }
    }

    override fun close() {
        keepAliveSingleThreadScheduledExecutor.close()
        connection.close()
    }

}
