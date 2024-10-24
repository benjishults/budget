package bps.budget.persistence.jdbc

import bps.budget.auth.BudgetAccess
import bps.budget.auth.CoarseAccess
import bps.budget.auth.User
import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.ChargeAccount
import bps.budget.model.DraftAccount
import bps.budget.model.DraftStatus
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.JdbcConfig
import bps.jdbc.JdbcFixture
import bps.jdbc.transact
import bps.jdbc.transactOrThrow
import bps.kotlin.Instrumentable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.net.URLEncoder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types.OTHER
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

    override fun getUserByLogin(login: String): User? =
        connection.transactOrThrow {
            prepareStatement(
                """
                |select *
                |from users u
                |         left join budget_access ba on u.id = ba.user_id
                |where u.login = ?
                """.trimMargin(),
            )
                .use { preparedStatement: PreparedStatement ->
                    preparedStatement.setString(1, login)
                    preparedStatement.executeQuery()
                        .use { resultSet: ResultSet ->
                            if (resultSet.next()) {
                                val budgets = mutableListOf<BudgetAccess>()
                                val id = resultSet.getObject("id", UUID::class.java)
                                do {
                                    val budgetName: String? = resultSet.getString("budget_name")
                                    if (budgetName !== null)
                                        budgets.add(
                                            BudgetAccess(
                                                budgetId = resultSet.getObject("budget_id", UUID::class.java),
                                                budgetName = budgetName,
                                                timeZone = resultSet.getString("time_zone")
                                                    ?.let { timeZone -> TimeZone.of(timeZone) }
                                                    ?: TimeZone.currentSystemDefault(),
                                                coarseAccess = resultSet.getString("coarse_access")
                                                    ?.let(CoarseAccess::valueOf),
                                            ),
                                        )
                                } while (resultSet.next())
                                User(id, login, budgets)
                            } else
                                null
                        }
                }
        }

    override fun createUser(login: String, password: String): UUID =
        UUID.randomUUID()
            .also { uuid: UUID ->
                connection.transactOrThrow {
                    prepareStatement("insert into users (login, id) values(?, ?)")
                        .use {
                            it.setString(1, login)
                            it.setUuid(2, uuid)
                            it.executeUpdate()
                        }
                }
            }

    override fun prepForFirstLoad() {
        connection.transactOrThrow {
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists users
(
    id    uuid         not null primary key,
    login varchar(110) not null unique
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists budgets
(
    id                 uuid not null primary key,
    general_account_id uuid not null unique
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists budget_access
(
    id            uuid         not null primary key,
    user_id       uuid         not null references users (id),
    budget_id     uuid         not null references budgets (id),
    budget_name   varchar(110) not null,
    time_zone     varchar(110) not null,
    -- if null, check fine_access
    coarse_access varchar,
    unique (user_id, budget_id)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create index if not exists lookup_budget_access_by_user
    on budget_access (user_id)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists access_details
(
    budget_access_id uuid    not null references budget_access (id),
    fine_access      varchar not null
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create index if not exists lookup_access_details_by_budget_access
    on access_details (budget_access_id)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists category_accounts
(
    id          uuid           not null unique,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_id   uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, budget_id)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists real_accounts
(
    id          uuid           not null unique,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_id   uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, budget_id)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists charge_accounts
(
    id          uuid           not null unique,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_id   uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, budget_id)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists draft_accounts
(
    id              uuid           not null unique,
    name            varchar(50)    not null,
    description     varchar(110)   not null default '',
    balance         numeric(30, 2) not null default 0.0,
    real_account_id uuid           not null references real_accounts (id),
    budget_id       uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, budget_id),
    unique (real_account_id, budget_id)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists account_active_periods
(
    id                  uuid      not null unique,
    start_date_utc      timestamp not null default '0001-01-01T00:00:00Z',
    end_date_utc        timestamp not null default '9999-12-31T23:59:59.999Z',
    category_account_id uuid      null references category_accounts (id),
    real_account_id     uuid      null references real_accounts (id),
    charge_account_id   uuid      null references charge_accounts (id),
    draft_account_id    uuid      null references draft_accounts (id),
    budget_id           uuid      not null references budgets (id)
        constraint only_one_account_per_period check
            ((real_account_id is not null and
              (category_account_id is null and draft_account_id is null and charge_account_id is null)) or
             (category_account_id is not null and
              (real_account_id is null and draft_account_id is null and charge_account_id is null)) or
             (charge_account_id is not null and
              (real_account_id is null and draft_account_id is null and category_account_id is null)) or
             (draft_account_id is not null and
              (category_account_id is null and real_account_id is null and charge_account_id is null))),
    unique (start_date_utc, draft_account_id, budget_id),
    unique (start_date_utc, category_account_id, budget_id),
    unique (start_date_utc, charge_account_id, budget_id),
    unique (start_date_utc, real_account_id, budget_id)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists transactions
(
    id                        uuid         not null unique,
    description               varchar(110) not null default '',
    timestamp_utc             timestamp    not null default now(),
    -- the transaction that clears this transaction
    cleared_by_transaction_id uuid         null,
    budget_id                 uuid         not null references budgets (id),
    primary key (id, budget_id)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_transaction_by_date
    on transactions (timestamp_utc desc, budget_id);
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists transaction_items
(
    id                  uuid           not null unique,
    transaction_id      uuid           not null references transactions (id),
    description         varchar(110)   null,
    amount              numeric(30, 2) not null,
    category_account_id uuid           null references category_accounts (id),
    real_account_id     uuid           null references real_accounts (id),
    charge_account_id   uuid           null references charge_accounts (id),
    draft_account_id    uuid           null references draft_accounts (id),
    draft_status        varchar        not null default 'none', -- 'none' 'outstanding' 'cleared', 'clearing'
    budget_id           uuid           not null references budgets (id),
    primary key (id, budget_id),
    constraint only_one_account_per_transaction_item check
        ((real_account_id is not null and
          category_account_id is null and
          draft_account_id is null and
          charge_account_id is null
             ) or
         (category_account_id is not null and
          real_account_id is null and
          draft_account_id is null and
          charge_account_id is null
             ) or
         (draft_account_id is not null and
          real_account_id is null and
          category_account_id is null and
          charge_account_id is null
             ) or
         (charge_account_id is not null and
          real_account_id is null and
          draft_account_id is null and
          category_account_id is null))
)
                """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_category_account_transaction_items_by_account
    on transaction_items (category_account_id, budget_id)
    where category_account_id is not null
                    """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_real_account_transaction_items_by_account
    on transaction_items (real_account_id, budget_id)
    where real_account_id is not null
                    """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_charge_account_transaction_items_by_account
    on transaction_items (charge_account_id, budget_id)
    where charge_account_id is not null
                    """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_draft_account_transaction_items_by_account
    on transaction_items (draft_account_id, budget_id)
    where draft_account_id is not null
                    """.trimIndent(),
                )
            }
        }
    }

    // TODO https://github.com/benjishults/budget/issues/14
//    /**
//     * if
//     * 1. [account] is real
//     * 2. there is a single other item in the transaction
//     * 3. that item has status 'clearing'
//     * then
//     * 1. pull the category items from the transactions cleared by this transaction
//     * 2. replace the 'clearing' item with those items
//     */
//    @Deprecated("This doesn't really work")
//    override fun fetchTransactionsInvolvingAccount(
//        account: Account,
//        data: BudgetData,
//        limit: Int,
//        offset: Int,
//    ): List<Transaction> =
//        connection.transactOrThrow {
//            // TODO if it is a clearing transaction, then create the full transaction by combining the cleared category items
//            //      with this.
//            // FIXME this limit offset stuff won't work the way I want... especially when offset > 0.
//            prepareStatement(
//                """
//                    |select t.timestamp_utc as timestamp_utc,
//                    |       t.id as transaction_id,
//                    |       t.description as description,
//                    |       i.amount      as item_amount,
//                    |       i.description as item_description,
//                    |       i.category_account_id,
//                    |       i.draft_account_id,
//                    |       i.real_account_id,
//                    |       i.charge_account_id,
//                    |       i.draft_status
//                    |from transactions t
//                    |  join transaction_items i on i.transaction_id = t.id
//                    |where t.budget_id = ?
//                    |  and t.id in (select tr.id
//                    |               from transactions tr
//                    |                 join transaction_items ti
//                    |                   on tr.id = ti.transaction_id
//                    |               where ti.${
//                    when (account) {
//                        is CategoryAccount -> "category_account_id"
//                        is ChargeAccount -> "charge_account_id"
//                        is RealAccount -> "real_account_id"
//                        is DraftAccount -> "draft_account_id"
//                        else -> throw Error("Unknown account type ${account::class}")
//                    }
//                } = ?
//                    |               order by tr.timestamp_utc desc, tr.id
//                    |               limit ?
//                    |               offset ?)
//            """.trimMargin(),
//            )
//                .use { selectTransactionAndItemsForAccount: PreparedStatement ->
//                    selectTransactionAndItemsForAccount
//                        .apply {
//                            setUuid(1, data.id)
//                            setUuid(2, account.id)
//                            setInt(3, limit)
//                            setInt(4, offset)
//                        }
//                    // TODO boy, this code is a mess.  looks like I was focussed on getting something to work and
//                    //      didn't clean it up after
//                    selectTransactionAndItemsForAccount
//                        .executeQuery()
//                        .use { result: ResultSet ->
//                            val returnValue: MutableList<Transaction> = mutableListOf()
//                            // NOTE this will be a running transaction and will switch to the next when one is done
//                            var runningTransactionId: UUID? = null
//                            var transactionBuilder: Transaction.Builder? = null
//                            while (result.next()) {
//                                // for each transaction item...
//                                result.getUuid("transaction_id")!!
//                                    .let { uuid: UUID ->
//                                        if (uuid != runningTransactionId) {
//                                            conditionallyAddCompleteTransactionToList(
//                                                runningTransactionId,
//                                                returnValue,
//                                                transactionBuilder,
//                                            )
//                                            runningTransactionId = uuid
//                                            transactionBuilder = initializeTransactionBuilder(result, uuid)
//                                        }
//                                    }
//                                val draftStatus: DraftStatus =
//                                    DraftStatus.valueOf(result.getString("draft_status"))
//                                if (draftStatus == DraftStatus.clearing) {
//                                    prepareStatement(
//                                        """
//                                            |select t.*,
//                                            |       i.amount      as item_amount,
//                                            |       i.description as item_description,
//                                            |       i.category_account_id
//                                            |from transactions t
//                                            |         join transaction_items i on i.transaction_id = t.id
//                                            |where t.id in (select id
//                                            |               from transactions
//                                            |               where cleared_by_transaction_id = ?)
//                                            |  and i.category_account_id is not null
//                                        """.trimMargin(),
//                                    )
//                                        .use { preparedStatement: PreparedStatement ->
//                                            preparedStatement.setUuid(1, runningTransactionId!!)
//                                            preparedStatement.executeQuery()
//                                                .use { result: ResultSet ->
//                                                    while (result.next()) {
//                                                        val itemAmount = result.getCurrencyAmount("item_amount")
//                                                        val itemDescription: String? =
//                                                            result.getString("item_description")
//                                                        transactionBuilder!!
//                                                            .categoryItemBuilders
//                                                            .maybeAddItemBuilder(
//                                                                result,
//                                                                itemAmount,
//                                                                itemDescription,
//                                                                data,
//                                                                "category",
//                                                                DraftStatus.none,
//                                                            ) { account: CategoryAccount ->
//                                                                this.categoryAccount = account
//                                                            }
//                                                    }
//                                                }
//                                        }
//                                } else {
//                                    val itemAmount = result.getCurrencyAmount("item_amount")
//                                    val itemDescription: String? = result.getString("item_description")
//                                    transactionBuilder!!
//                                        .categoryItemBuilders
//                                        .maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "category",
//                                            draftStatus,
//                                        ) { account: CategoryAccount ->
//                                            this.categoryAccount = account
//                                        }
//                                    transactionBuilder!!
//                                        .realItemBuilders.maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "real",
//                                            draftStatus,
//                                        ) { account: RealAccount ->
//                                            realAccount = account
//                                        }
//                                    transactionBuilder!!
//                                        .chargeItemBuilders.maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "charge",
//                                            draftStatus,
//                                        ) { account: ChargeAccount ->
//                                            chargeAccount = account
//                                        }
//                                    transactionBuilder!!
//                                        .draftItemBuilders
//                                        .maybeAddItemBuilder(
//                                            result,
//                                            itemAmount,
//                                            itemDescription,
//                                            data,
//                                            "draft",
//                                            draftStatus,
//                                        ) { account: DraftAccount ->
//                                            draftAccount = account
//                                        }
//                                }
//                            }
//                            conditionallyAddCompleteTransactionToList(
//                                runningTransactionId,
//                                returnValue,
//                                transactionBuilder,
//                            )
//                            returnValue.toList()
//                        }
//                }
//        }

    override fun fetchTransactionItemsInvolvingAccount(
        account: Account,
        limit: Int,
        offset: Int,
//        accountIdToAccountMap: Map<UUID, Account>,
        balanceAtEndOfPage: BigDecimal,
    ): List<BudgetDao.ExtendedTransactionItem> =
        buildList {
            connection.transactOrThrow {
                // TODO if it is a clearing transaction, then create the full transaction by combining the cleared category items
                //      with this.
                prepareStatement(
                    """
                    |select t.id as transaction_id,
                    |       t.description as transaction_description,
                    |       t.timestamp_utc as transaction_timestamp,
                    |       i.id as item_id,
                    |       i.amount,
                    |       i.description,
                    |       i.draft_status
                    |from transactions t
                    |         join transaction_items i
                    |            on i.transaction_id = t.id
                    |                and t.budget_id = i.budget_id
                    |where t.budget_id = ?
                    |  and i.${account.type}_account_id = ?
                    |order by t.timestamp_utc desc, t.id
                    |limit ?
                    |offset ?
            """.trimMargin(),
                )
                    .use { selectExtendedTransactionItemsForAccount: PreparedStatement ->
                        selectExtendedTransactionItemsForAccount
                            .apply {
                                setUuid(1, account.budgetId)
                                setUuid(2, account.id)
                                setInt(3, limit)
                                setInt(4, offset)
                            }
                        selectExtendedTransactionItemsForAccount
                            .executeQuery()
                            .use { result: ResultSet ->
                                with(result) {
                                    var runningBalance = balanceAtEndOfPage
                                    while (next()) {
                                        val transactionId: UUID = getUuid("transaction_id")!!
                                        val transactionDescription: String = getString("transaction_description")!!
                                        val transactionTimestamp: Instant = getInstant("transaction_timestamp")
                                        val id: UUID = getUuid("item_id")!!
                                        val amount: BigDecimal = getCurrencyAmount("amount")
                                        val description: String? = getString("description")
                                        val draftStatus: DraftStatus =
                                            DraftStatus.valueOf(getString("draft_status"))
                                        this@buildList.add(
                                            BudgetDao.ExtendedTransactionItem(
                                                item = Transaction.ItemBuilder(
                                                    id = id,
                                                    amount = amount,
                                                    description = description,
                                                    draftStatus = draftStatus,
                                                )
                                                    .apply {
                                                        with(account) {
                                                            this@apply.itemBuilderSetter()
                                                        }
                                                    },
                                                transactionId = transactionId,
                                                transactionDescription = transactionDescription,
                                                transactionTimestamp = transactionTimestamp,
                                                budgetDao = this@JdbcDao,
                                                budgetId = account.budgetId,
                                                accountBalanceAfterItem = runningBalance,
                                            ),
                                        )
                                        runningBalance -= amount
                                        // TODO https://github.com/benjishults/budget/issues/14
//                                    if (draftStatus == DraftStatus.clearing) {
//                                        prepareStatement(
//                                            """
//                                            |select t.*,
//                                            |       i.amount      as item_amount,
//                                            |       i.description as item_description,
//                                            |       i.category_account_id
//                                            |from transactions t
//                                            |         join transaction_items i on i.transaction_id = t.id
//                                            |where t.id in (select id
//                                            |               from transactions
//                                            |               where cleared_by_transaction_id = ?)
//                                            |  and i.category_account_id is not null
//                                        """.trimMargin(),
//                                        )
//                                            .use { selectClearedByExtendedTransactionItems: PreparedStatement ->
//                                                selectClearedByExtendedTransactionItems.setUuid(1, runningTransactionId!!)
//                                                selectClearedByExtendedTransactionItems.executeQuery()
//                                                    .use { result: ResultSet ->
//                                                        while (result.next()) {
//                                                            val itemAmount = result.getCurrencyAmount("item_amount")
//                                                            val itemDescription: String? =
//                                                                result.getString("item_description")
//                                                            transactionBuilder!!
//                                                                .categoryItemBuilders
//                                                                .maybeAddItemBuilder(
//                                                                    result,
//                                                                    itemAmount,
//                                                                    itemDescription,
//                                                                    data,
//                                                                    "category",
//                                                                    DraftStatus.none,
//                                                                ) { account: CategoryAccount ->
//                                                                    this.categoryAccount = account
//                                                                }
//                                                        }
//                                                    }
//                                            }
//                                    } else {
                                    }
                                }
                            }
                    }
            }
        }

    override fun getTransactionOrNull(
        transactionId: UUID,
        budgetId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): Transaction? =
        connection.transactOrThrow {
            prepareStatement(
                """
                    |select t.description as transaction_description,
                    |       t.timestamp_utc as transaction_timestamp,
                    |       t.cleared_by_transaction_id,
                    |       i.category_account_id,
                    |       i.real_account_id,
                    |       i.draft_account_id,
                    |       i.charge_account_id,
                    |       i.id,
                    |       i.amount,
                    |       i.description,
                    |       i.draft_status
                    |from transactions t
                    |         join transaction_items i
                    |              on i.transaction_id = t.id
                    |                  and t.budget_id = i.budget_id
                    |where t.budget_id = ?
                    |  and t.id = ?
                """.trimMargin(),
            )
                .use { selectTransactionAndItems: PreparedStatement ->
                    selectTransactionAndItems
                        .apply {
                            setUuid(1, budgetId)
                            setUuid(2, transactionId)
                        }
                        .executeQuery()
                        .use { result: ResultSet ->
                            if (result.next()) {
                                val transactionBuilder =
                                    initializeTransactionBuilderWithFirstItem(
                                        result,
                                        transactionId,
                                        accountIdToAccountMap,
                                    )
                                while (result.next()) {
                                    // for each transaction item...
                                    transactionBuilder.populateItem(result, accountIdToAccountMap)
                                }
                                transactionBuilder.build()
                            } else
                                null
                        }
                }
        }

    private fun initializeTransactionBuilderWithFirstItem(
        result: ResultSet,
        transactionId: UUID,
        accountIdToAccountMap: Map<UUID, Account>,
    ): Transaction.Builder =
        Transaction
            .Builder()
            .apply {
                id = transactionId
                description = result.getString("transaction_description")
                timestamp = result.getInstant("transaction_timestamp")
                populateItem(result, accountIdToAccountMap)
            }

    private fun Transaction.Builder.populateItem(
        result: ResultSet,
        accountIdToAccountMap: Map<UUID, Account>,
    ) {
        (result.getUuid("category_account_id")
            ?: result.getUuid("real_account_id")
            ?: result.getUuid("charge_account_id")
            ?: result.getUuid("draft_account_id")!!)
            .let { uuid ->
                val account: Account = accountIdToAccountMap.get(uuid)!!
                with(account) {
                    addItem(
                        result.getCurrencyAmount("amount"),
                        result.getString("description"),
                        DraftStatus.valueOf(result.getString("draft_status")),
                        result.getUuid("id")!!,
                    )
                }
            }
    }

    private fun Connection.createStagingDraftAccountsTable() {
        createStatement().use { createTablesStatement: Statement ->
            createTablesStatement.executeUpdate(
                """
    create temp table if not exists staged_draft_accounts
    (
        id              uuid           not null,
        gen             integer        not null generated always as identity,
        name            varchar(50)    not null,
        description     varchar(110)   not null default '',
        balance         numeric(30, 2) not null default 0.0,
        real_account_id uuid           not null,
        budget_id       uuid           not null
    )
        on commit drop
                        """.trimIndent(),
            )
        }
    }

    private fun Connection.createStagingAccountsTable(tableNamePrefix: String) {
        createStatement().use { createTablesStatement: Statement ->
            createTablesStatement.executeUpdate(
                """
    create temp table if not exists staged_${tableNamePrefix}_accounts
    (
        id          uuid           not null,
        gen         integer        not null generated always as identity,
        name        varchar(50)    not null,
        description varchar(110)   not null default '',
        balance     numeric(30, 2) not null default 0.0,
        budget_id   uuid           not null
    )
        on commit drop
                        """.trimIndent(),
            )
        }
    }

    data class BudgetDataInfo(
        val generalAccountId: UUID,
        val timeZone: TimeZone,
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
                val (generalAccountId: UUID, timeZone: TimeZone, budgetName: String) =
                    prepareStatement(
                        """
                            select b.general_account_id, ba.time_zone, ba.budget_name
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
                                            result.getObject("general_account_id", UUID::class.java),
                                            result.getString("time_zone")
                                                ?.let { timeZone -> TimeZone.of(timeZone) }
                                                ?: TimeZone.currentSystemDefault(),
                                            result.getString("budget_name"),
                                        )
                                    } else
                                        throw DataConfigurationException("Budget data not found for name: ${config.budgetName}")
                                }
                        }
                // TODO pull out duplicate code in these next three sections
                val categoryAccounts: List<CategoryAccount> =
                    getAccounts("category", budgetId, ::CategoryAccount)
                val generalAccount: CategoryAccount =
                    categoryAccounts.find {
                        it.id == generalAccountId
                    }!!
                val realAccounts: List<RealAccount> =
                    getAccounts("real", budgetId, ::RealAccount)
                val chargeAccounts: List<ChargeAccount> =
                    getAccounts("charge", budgetId, ::ChargeAccount)
                val draftAccounts: List<DraftAccount> =
                    prepareStatement(
                        """
select acc.*
from draft_accounts acc
         join account_active_periods aap
              on acc.id = aap.draft_account_id
                  and acc.budget_id = aap.budget_id
where acc.budget_id = ?
  and now() > aap.start_date_utc
  and now() < aap.end_date_utc
""".trimIndent(),
                    )
                        .use { getDraftAccountsStatement ->
                            getDraftAccountsStatement.setUuid(1, budgetId)
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
                                                        it.id.toString() == result.getString(
                                                            "real_account_id",
                                                        )
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
            ?: throw DataConfigurationException("Transaction rolled back.")

    private fun <T : Account> Connection.getAccounts(
        tablePrefix: String,
        budgetId: UUID,
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
    ): List<T> =
        prepareStatement(
            """
select acc.*
from ${tablePrefix}_accounts acc
         join account_active_periods aap
              on acc.id = aap.${tablePrefix}_account_id
                  and acc.budget_id = aap.budget_id
where acc.budget_id = ?
  and now() > aap.start_date_utc
  and now() < aap.end_date_utc
""".trimIndent(),
        )
            .use { getAccounts: PreparedStatement ->
                getAccounts.setUuid(1, budgetId)
                getAccounts.executeQuery()
                    .use { result ->
                        buildList {
                            while (result.next()) {
                                add(
                                    factory(
                                        result.getString("name"),
                                        result.getString("description"),
                                        result.getObject("id", UUID::class.java),
                                        result.getCurrencyAmount("balance"),
                                        budgetId,
                                    ),
                                )
                            }
                        }
                    }
            }

    /**
     * @throws IllegalArgumentException if either the [draftTransactionItems] or the [clearingTransaction] is not what
     * we expect
     */
    override fun clearCheck(
        draftTransactionItems: List<Transaction.Item>,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) {
        // require clearTransaction is a simple draft transaction(s) clearing transaction
        require(clearingTransaction.draftItems.isNotEmpty())
        require(clearingTransaction.categoryItems.isEmpty())
        require(clearingTransaction.chargeItems.isEmpty())
        require(clearingTransaction.realItems.size == 1)
        val realTransactionItem: Transaction.Item = clearingTransaction.realItems.first()
        val realAccount: RealAccount = realTransactionItem.realAccount!!
        require(
            clearingTransaction
                .draftItems
                .first()
                .draftAccount
                ?.realCompanion == realAccount,
        )
        // require draftTransactionItems to be what we expect
        require(draftTransactionItems.isNotEmpty())
        require(
            draftTransactionItems.all {
                it.draftAccount
                    ?.realCompanion == realAccount
                        &&
                        with(it.transaction) {
                            realItems.isEmpty()
                                    &&
                                    chargeItems.isEmpty()
                                    &&
                                    draftItems.size == 1
                        }
            },
        )
        require(
            draftTransactionItems
                .mapTo(mutableSetOf()) { it.transaction }
                .size ==
                    draftTransactionItems.size,
        )
        require(
            draftTransactionItems
                .fold(BigDecimal.ZERO.setScale(2)) { sum, transactionItem ->
                    sum + transactionItem.amount
                } ==
                    -realTransactionItem.amount,
        )
        connection.transactOrThrow {
            draftTransactionItems
                .forEach { draftTransactionItem ->
                    prepareStatement(
                        """
                            |update transaction_items ti
                            |set draft_status = 'cleared'
                            |where ti.budget_id = ?
                            |and ti.transaction_id = ?
                            |and ti.draft_account_id = ?
                            |and ti.draft_status = 'outstanding'
                        """.trimMargin(),
                    )
                        .use { statement ->
                            statement.setUuid(1, budgetId)
                            statement.setUuid(2, draftTransactionItem.transaction.id)
                            statement.setUuid(3, draftTransactionItem.draftAccount!!.id)
                            if (statement.executeUpdate() != 1)
                                throw IllegalStateException("Check being cleared not found in DB")
                        }
                    prepareStatement(
                        """
                            |update transactions t
                            |set cleared_by_transaction_id = ?
                            |where t.id = ?
                            |and t.budget_id = ?
                        """.trimMargin(),
                    )
                        .use { statement ->
                            statement.setUuid(1, clearingTransaction.id)
                            statement.setUuid(2, draftTransactionItem.transaction.id)
                            statement.setUuid(3, budgetId)
                            statement.executeUpdate()
                        }
                }
            insertTransactionPreparedStatement(clearingTransaction, budgetId)
                .use { insertTransaction: PreparedStatement ->
                    insertTransaction.executeUpdate()
                }
            insertTransactionItemsPreparedStatement(clearingTransaction, budgetId)
                .use { insertTransactionItem: PreparedStatement ->
                    insertTransactionItem.executeUpdate()
                }
            updateBalances(clearingTransaction, budgetId)
        }

    }

    /**
     * @throws IllegalArgumentException if either the [clearedItems] or the [billPayTransaction] is not what
     * we expect
     */
// TODO allow checks to pay credit card bills
    override fun commitCreditCardPayment(
        clearedItems: List<BudgetDao.ExtendedTransactionItem>,
        billPayTransaction: Transaction,
        budgetId: UUID,
    ) {
        // require billPayTransaction is a simple real transfer
        require(billPayTransaction.draftItems.isEmpty())
        require(billPayTransaction.categoryItems.isEmpty())
        require(billPayTransaction.chargeItems.size == 1)
        require(billPayTransaction.realItems.size == 1)
        val billPayChargeTransactionItem: Transaction.Item =
            billPayTransaction.chargeItems.first()
        val chargeAccount: ChargeAccount =
            billPayChargeTransactionItem.chargeAccount!!
        // require clearedItems to be what we expect
        require(clearedItems.isNotEmpty())
        require(
            clearedItems.all {
                it.item.chargeAccount == chargeAccount
            },
        )
        require(
            clearedItems
                .fold(BigDecimal.ZERO.setScale(2)) { sum, transactionItem: BudgetDao.ExtendedTransactionItem ->
                    sum + transactionItem.item.amount!!
                } ==
                    -billPayChargeTransactionItem.amount,
        )
        connection.transactOrThrow {
            clearedItems
                .forEach { chargeTransactionItem: BudgetDao.ExtendedTransactionItem ->
                    prepareStatement(
                        """
                            |update transaction_items ti
                            |set draft_status = 'cleared'
                            |where ti.budget_id = ?
                            |and ti.transaction_id = ?
                            |and ti.charge_account_id = ?
                            |and ti.draft_status = 'outstanding'
                        """.trimMargin(),
                    )
                        .use { statement ->
                            statement.setUuid(1, budgetId)
                            statement.setUuid(2, chargeTransactionItem.transactionId)
                            statement.setUuid(3, chargeTransactionItem.item.chargeAccount!!.id)
                            if (statement.executeUpdate() != 1)
                                throw IllegalStateException("Charge being cleared not found in DB")
                        }
                    prepareStatement(
                        """
                            |update transactions t
                            |set cleared_by_transaction_id = ?
                            |where t.id = ?
                            |and t.budget_id = ?
                        """.trimMargin(),
                    )
                        .use { statement ->
                            statement.setUuid(1, billPayTransaction.id)
                            statement.setUuid(2, chargeTransactionItem.transactionId)
                            statement.setUuid(3, budgetId)
                            statement.executeUpdate()
                        }
                }
            insertTransactionPreparedStatement(billPayTransaction, budgetId)
                .use { insertTransaction: PreparedStatement ->
                    insertTransaction.executeUpdate()
                }
            insertTransactionItemsPreparedStatement(billPayTransaction, budgetId)
                .use { insertTransactionItem: PreparedStatement ->
                    insertTransactionItem.executeUpdate()
                }
            updateBalances(billPayTransaction, budgetId)
        }

    }

    /**
     * Inserts the transaction records and updates the account balances in the DB.
     */
    override fun commit(
        transaction: Transaction,
        budgetId: UUID,
        saveBalances: Boolean,
    ) {
        connection.transactOrThrow {
            insertTransactionPreparedStatement(transaction, budgetId)
                .use { insertTransaction: PreparedStatement ->
                    insertTransaction.executeUpdate()
                }
            insertTransactionItemsPreparedStatement(transaction, budgetId)
                .use { insertTransactionItem: PreparedStatement ->
                    insertTransactionItem.executeUpdate()
                }
            if (saveBalances) updateBalances(transaction, budgetId)
        }
    }

    private fun Connection.updateBalances(transaction: Transaction, budgetId: UUID) {
        val categoryAccountUpdateStatement = """
            update category_accounts
            set balance = balance + ?
            where id = ? and budget_id = ?""".trimIndent()
        val realAccountUpdateStatement = """
            update real_accounts
            set balance = balance + ?
            where id = ? and budget_id = ?""".trimIndent()
        val chargeAccountUpdateStatement = """
            update charge_accounts
            set balance = balance + ?
            where id = ? and budget_id = ?""".trimIndent()
        val draftAccountUpdateStatement = """
            update draft_accounts
            set balance = balance + ?
            where id = ? and budget_id = ?""".trimIndent()
        buildMap {
            put(
                categoryAccountUpdateStatement,
                buildList {
                    transaction.categoryItems.forEach { transactionItem: Transaction.Item ->
                        add(
                            transactionItem.categoryAccount!!.id to
                                    transactionItem.amount,
                        )
                    }
                },
            )
            put(
                realAccountUpdateStatement,
                buildList {
                    transaction.realItems.forEach { transactionItem: Transaction.Item ->
                        add(
                            transactionItem.realAccount!!.id to
                                    transactionItem.amount,
                        )
                    }
                },
            )
            put(
                chargeAccountUpdateStatement,
                buildList {
                    transaction.chargeItems.forEach { transactionItem: Transaction.Item ->
                        add(
                            transactionItem.chargeAccount!!.id to
                                    transactionItem.amount,
                        )
                    }
                },
            )
            put(
                draftAccountUpdateStatement,
                buildList {
                    transaction.draftItems.forEach { transactionItem: Transaction.Item ->
                        add(
                            transactionItem.draftAccount!!.id to
                                    transactionItem.amount,
                        )
                    }
                },
            )
        }
            .forEach { (statement: String, accountIdAmountPair: List<Pair<UUID, BigDecimal>>) ->
                prepareStatement(statement).use { preparedStatement: PreparedStatement ->
                    accountIdAmountPair.forEach { (id, amount) ->
                        preparedStatement.setBigDecimal(1, amount)
                        preparedStatement.setUuid(2, id)
                        preparedStatement.setUuid(3, budgetId)
                        preparedStatement.executeUpdate()
                    }
                }
            }
    }

    private fun Connection.insertTransactionItemsPreparedStatement(
        transaction: Transaction,
        budgetId: UUID,
    ): PreparedStatement {
        val transactionItemCounter =
            transaction.categoryItems.size + transaction.realItems.size + transaction.draftItems.size + transaction.chargeItems.size
        val insertSql = buildString {
            var counter = transactionItemCounter
            append("insert into transaction_items (id, transaction_id, description, amount, draft_status, budget_id, category_account_id, real_account_id, charge_account_id, draft_account_id) values ")
            if (counter-- > 0) {
                append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                while (counter-- > 0) {
                    append(", (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                }
            }
        }
        var parameterIndex = 1
        val transactionItemInsert = prepareStatement(insertSql)
        transaction.categoryItems.forEach { transactionItem: Transaction.Item ->
            parameterIndex += setStandardProperties(
                transactionItemInsert,
                parameterIndex,
                transaction,
                transactionItem,
                budgetId,
            )
            transactionItemInsert.setUuid(parameterIndex++, transactionItem.categoryAccount!!.id)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
        }
        transaction.realItems.forEach { transactionItem: Transaction.Item ->
            parameterIndex += setStandardProperties(
                transactionItemInsert,
                parameterIndex,
                transaction,
                transactionItem,
                budgetId,
            )
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setUuid(parameterIndex++, transactionItem.realAccount!!.id)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
        }
        transaction.chargeItems.forEach { transactionItem: Transaction.Item ->
            parameterIndex += setStandardProperties(
                transactionItemInsert,
                parameterIndex,
                transaction,
                transactionItem,
                budgetId,
            )
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setUuid(parameterIndex++, transactionItem.chargeAccount!!.id)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
        }
        transaction.draftItems.forEach { transactionItem: Transaction.Item ->
            parameterIndex += setStandardProperties(
                transactionItemInsert,
                parameterIndex,
                transaction,
                transactionItem,
                budgetId,
            )
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setUuid(parameterIndex++, transactionItem.draftAccount!!.id)
        }
        return transactionItemInsert
    }

    /**
     * Sets a set of standard parameter starting at [parameterIndex]
     * @return the number of parameters set.
     */
    private fun setStandardProperties(
        transactionItemInsert: PreparedStatement,
        parameterIndex: Int,
        transaction: Transaction,
        transactionItem: Transaction.Item,
        budgetId: UUID,
    ): Int {
        transactionItemInsert.setUuid(parameterIndex, transactionItem.id)
        transactionItemInsert.setUuid(parameterIndex + 1, transaction.id)
        transactionItemInsert.setString(parameterIndex + 2, transactionItem.description)
        transactionItemInsert.setBigDecimal(parameterIndex + 3, transactionItem.amount)
        transactionItemInsert.setString(parameterIndex + 4, transactionItem.draftStatus.name)
        transactionItemInsert.setUuid(parameterIndex + 5, budgetId)
        return 6
    }

    private fun Connection.insertTransactionPreparedStatement(
        transaction: Transaction,
        budgetId: UUID,
    ): PreparedStatement {
        val insertTransaction: PreparedStatement = prepareStatement(
            """
                insert into transactions (id, description, timestamp_utc, budget_id) VALUES
                (?, ?, ?, ?)
            """.trimIndent(),
        )
        insertTransaction.setUuid(1, transaction.id)
        insertTransaction.setString(2, transaction.description)
        insertTransaction.setTimestamp(3, transaction.timestamp)
        insertTransaction.setUuid(4, budgetId)
        return insertTransaction
    }

    override fun save(data: BudgetData, user: User) {
        require(data.validate())
        // create user and budget if it isn't there
        connection.transactOrThrow {
            val generalAccountId: UUID = data.generalAccount.id
            prepareStatement(
                """
                insert into users (id, login)
                values (?, ?) on conflict do nothing""",
            )
                .use { createBudgetStatement: PreparedStatement ->
                    createBudgetStatement.setUuid(1, user.id)
                    createBudgetStatement.setString(2, user.login)
                    createBudgetStatement.executeUpdate()
                }
            prepareStatement(
                """
                insert into budgets (id, general_account_id)
                values (?, ?) on conflict do nothing""",
            )
                .use { createBudgetStatement: PreparedStatement ->
                    createBudgetStatement.setUuid(1, data.id)
                    createBudgetStatement.setUuid(2, generalAccountId)
                    createBudgetStatement.executeUpdate()
                }
            prepareStatement(
                """
                insert into budget_access (id, budget_id, user_id, time_zone, budget_name)
                values (?, ?, ?, ?, ?) on conflict do nothing""".trimIndent(),
            )
                .use { createBudgetStatement: PreparedStatement ->
                    createBudgetStatement.setUuid(1, UUID.randomUUID())
                    createBudgetStatement.setUuid(2, data.id)
                    createBudgetStatement.setUuid(3, user.id)
                    createBudgetStatement.setString(4, data.timeZone.id)
                    createBudgetStatement.setString(5, config.budgetName)
                    createBudgetStatement.executeUpdate()
                }
            // create accounts that aren't there and update those that are
            createStagingAccountsTable("category")
            upsertAccountData(data.categoryAccounts, CATEGORY_ACCOUNT_TABLE_NAME, data.id)
            createStagingAccountsTable("real")
            upsertAccountData(data.realAccounts, REAL_ACCOUNT_TABLE_NAME, data.id)
            createStagingAccountsTable("charge")
            upsertAccountData(data.chargeAccounts, CHARGE_ACCOUNT_TABLE_NAME, data.id)
            createStagingDraftAccountsTable()
            upsertAccountData(data.draftAccounts, DRAFT_ACCOUNT_TABLE_NAME, data.id)
            // TODO mark deleted accounts as in-active
            //      see account_active_periods which is currently unused
        }
    }

    override fun deleteUser(userId: UUID) {
        connection.transactOrThrow {
            prepareStatement("delete from budget_access where user_id = ?")
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, userId)
                    statement.executeUpdate()
                }
            prepareStatement("delete from users where id = ?")
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, userId)
                    statement.executeUpdate()
                }
        }
    }


    override fun deleteUserByLogin(login: String) {
        connection.transactOrThrow {
            getUserIdByLogin(login)
                ?.let { userId: UUID ->
                    prepareStatement("delete from budget_access where user_id = ?")
                        .use { statement: PreparedStatement ->
                            statement.setUuid(1, userId)
                            statement.executeUpdate()
                        }
                    prepareStatement("delete from users where login = ?")
                        .use { statement: PreparedStatement ->
                            statement.setString(1, login)
                            statement.executeUpdate()
                        }
                }
        }
    }

    private fun Connection.getUserIdByLogin(login: String): UUID? =
        prepareStatement("select id from users where login = ?")
            .use { statement: PreparedStatement ->
                statement.setString(1, login)
                statement.executeQuery()
                    .use { resultSet: ResultSet ->
                        if (resultSet.next()) {
                            resultSet.getObject("id", UUID::class.java)
                        } else
                            null
                    }
            }

    override fun deleteBudget(budgetId: UUID) {
        connection.transactOrThrow {
            prepareStatement("delete from budget_access where budget_id = ?")
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, budgetId)
                    statement.executeUpdate()
                }
            prepareStatement("delete from budgets where id = ?")
                .use { statement: PreparedStatement ->
                    statement.setUuid(1, budgetId)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * Must be called within a transaction with manual commits
     */
    private fun Connection.upsertAccountData(accounts: List<Account>, tableName: String, budgetId: UUID) {
        // if a DB-active account is not in the list, deactivate it in DB
        val currentAccountIds: Set<UUID> =
            accounts
                .map { it.id }
                .toSet()
        val activityIdsToBeDeactivated: List<UUID> =
            getFullListOfActiveAccountsWithActivityRecords(tableName, budgetId)
                .filter { it.first !in currentAccountIds }
                .map { it.second }
        activityIdsToBeDeactivated.forEach { activityId: UUID ->
            prepareStatement(
                """
update account_active_periods
set end_date_utc = now()
where id = ?
                """.trimIndent(),
            )
                .use { deactivateActivityPeriod: PreparedStatement ->
                    deactivateActivityPeriod.setUuid(1, activityId)
                    deactivateActivityPeriod.executeUpdate()
                }
        }
        accounts.forEach { account ->
            prepareStatement(
                """
                insert into staged_$tableName (id, name, description, balance,${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) "real_account_id, " else ""} budget_id)
                VALUES (?, ?, ?, ?,${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) " ?," else ""} ?)
                on conflict do nothing
            """.trimIndent(),
            )
                .use { createStagedAccountStatement: PreparedStatement ->
                    var parameterIndex = 1
                    createStagedAccountStatement.setUuid(parameterIndex++, account.id)
                    createStagedAccountStatement.setString(parameterIndex++, account.name)
                    createStagedAccountStatement.setString(parameterIndex++, account.description)
                    createStagedAccountStatement.setBigDecimal(parameterIndex++, account.balance)
                    if (tableName == DRAFT_ACCOUNT_TABLE_NAME) {
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
                merge into $tableName as t
                    using staged_$tableName as s
                    on (t.id = s.id or t.name = s.name) and t.budget_id = s.budget_id
                    when matched then
                        update
                        set name = s.name,
                            description = s.description,
                            balance = s.balance
                    when not matched then
                        insert (id, name, description, balance, ${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) "real_account_id, " else ""} budget_id)
                        values (s.id, s.name, s.description, s.balance, ${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) "s.real_account_id, " else ""} s.budget_id);
                """.trimIndent(),
            )
                .use { createAccountStatement: PreparedStatement ->
                    createAccountStatement.executeUpdate()
                }
            prepareStatement(
                """
                    insert into account_active_periods (id, ${foreignKeyNameForTable(tableName)}, budget_id)
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

    private fun Connection.getFullListOfActiveAccountsWithActivityRecords(
        tableName: String,
        budgetId: UUID,
    ): List<Pair<UUID, UUID>> =
        buildList {
            prepareStatement(
                """
    select acc.id as account_id, aap.id as activity_id
    from $tableName acc
             join account_active_periods aap
                  on acc.id = aap.${foreignKeyNameForTable(tableName)}
                      and acc.budget_id = aap.budget_id
    where acc.budget_id = ?
      and now() > aap.start_date_utc
      and now() < aap.end_date_utc
                      """.trimIndent(),
            )
                .use { selectAllAccounts ->
                    selectAllAccounts.setUuid(1, budgetId)
                    selectAllAccounts.executeQuery()
                        .use { resultSet: ResultSet ->
                            while (resultSet.next()) {
                                add(resultSet.getUuid("account_id")!! to resultSet.getUuid("activity_id")!!)
                            }
                        }
                }
        }

    fun foreignKeyNameForTable(tableName: String) = "${tableName.substring(0, tableName.length - 1)}_id"

    override fun close() {
        keepAliveSingleThreadScheduledExecutor.close()
        connection.close()
    }

    companion object {
        const val CATEGORY_ACCOUNT_TABLE_NAME = "category_accounts"
        const val REAL_ACCOUNT_TABLE_NAME = "real_accounts"
        const val CHARGE_ACCOUNT_TABLE_NAME = "charge_accounts"
        const val DRAFT_ACCOUNT_TABLE_NAME = "draft_accounts"
    }

}

/**
 * This assumes that the DB [Timestamp] is stored in UTC.
 */
fun Timestamp.toLocalDateTime(timeZone: TimeZone): LocalDateTime =
    this
        .toInstant()
        .toKotlinInstant()
        .toLocalDateTime(timeZone)
