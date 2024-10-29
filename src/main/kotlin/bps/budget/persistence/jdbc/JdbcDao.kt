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
import bps.budget.persistence.BudgetDao.ExtendedTransactionItem
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

    // NOTE be sure to make this volatile if we go multithreaded
    final var errorState: Throwable? = null
        private set

    fun <T> catchCommitErrorState(block: () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            errorState = e
            throw e
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
        catchCommitErrorState {
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
    on transactions (timestamp_utc desc, budget_id);
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

    /**
     * @param balanceAtEndOfPage is the balance of the account after the latest transaction on the page being requested.
     * If `null`, then [ExtendedTransactionItem.accountBalanceAfterItem] will be `null` for each [ExtendedTransactionItem] returned.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <A : Account> fetchTransactionItemsInvolvingAccount(
        account: A,
        limit: Int,
        offset: Int,
        balanceAtEndOfPage: BigDecimal?,
    ): List<ExtendedTransactionItem<A>> =
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
                    |  and i.account_id = ?
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
                                    var runningBalance: BigDecimal? = balanceAtEndOfPage

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
                                            with(account) {
                                                extendedTransactionItemFactory(
                                                    id = id,
                                                    amount = amount,
                                                    description = description,
                                                    draftStatus = draftStatus,
                                                    transactionId = transactionId,
                                                    transactionDescription = transactionDescription,
                                                    transactionTimestamp = transactionTimestamp,
                                                    accountBalanceAfterItem = runningBalance,
                                                )
                                            } as ExtendedTransactionItem<A>,
                                        )
                                        if (runningBalance !== null)
                                            runningBalance -= amount
                                    }
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
                    |       i.account_id,
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
        with(accountIdToAccountMap[result.getUuid("account_id")!!]!!) {
            addItemBuilderTo(
                result.getCurrencyAmount("amount"),
                result.getString("description"),
                DraftStatus.valueOf(result.getString("draft_status")),
                result.getUuid("id")!!,
            )
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
                val draftAccounts: List<DraftAccount> = // getAccounts("draft", budgetId, ::DraftAccount)
                    prepareStatement(
                        """
select acc.*
from accounts acc
         join account_active_periods aap
              on acc.id = aap.account_id
                  and acc.budget_id = aap.budget_id
where acc.budget_id = ?
  and acc.type = 'draft'
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
        type: String,
        budgetId: UUID,
        factory: (String, String, UUID, BigDecimal, UUID) -> T,
    ): List<T> =
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
            .use { getAccounts: PreparedStatement ->
                getAccounts.setUuid(1, budgetId)
                getAccounts.setString(2, type)
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
     * 1. Sets the [Transaction.Item.draftStatus] to [DraftStatus.cleared] for each [draftTransactionItems]
     * 2. Sets the "cleared-by" relation in the DB.
     * 3. Commits the new [clearingTransaction]
     * 4. Updates account balances
     * @throws IllegalArgumentException if either the [draftTransactionItems] or the [clearingTransaction] is not what
     * we expect
     */
    override fun clearCheck(
        draftTransactionItems: List<Transaction.Item<DraftAccount>>,
        clearingTransaction: Transaction,
        budgetId: UUID,
    ) =
        catchCommitErrorState {
            // require clearTransaction is a simple draft transaction(s) clearing transaction
            // with a single real item
            require(clearingTransaction.draftItems.isNotEmpty())
            require(clearingTransaction.categoryItems.isEmpty())
            require(clearingTransaction.chargeItems.isEmpty())
            require(clearingTransaction.realItems.size == 1)
            val realTransactionItem: Transaction.Item<RealAccount> = clearingTransaction.realItems.first()
            val realAccount: RealAccount = realTransactionItem.account
            // the clearing transaction's draft item is on the drafts account related to that real account
            require(
                clearingTransaction
                    .draftItems
                    .first()
                    .account
                    .realCompanion == realAccount,
            )
            require(draftTransactionItems.isNotEmpty())
            // each transaction item is on the correct account
            // and the transactions they are part of were check-writing transactions
            require(
                draftTransactionItems
                    .all {
                        it
                            .account
                            .realCompanion == realAccount &&
                                with(it.transaction) {
                                    realItems.isEmpty() &&
                                            chargeItems.isEmpty() &&
                                            draftItems.size == 1
                                }
                    },
            )
            // each transaction item comes from a different transaction
            require(
                draftTransactionItems
                    .mapTo(mutableSetOf()) { it.transaction }
                    .size ==
                        draftTransactionItems.size,
            )
            // the draft transactions items' amount sum is the amount being taken out of the real account
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
                            |and ti.id = ?
                            |and ti.draft_status = 'outstanding'
                        """.trimMargin(),
                        )
                            .use { statement ->
                                statement.setUuid(1, budgetId)
                                statement.setUuid(2, draftTransactionItem.id)
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
     * 1. Sets the [Transaction.Item.draftStatus] to [DraftStatus.cleared] for each [clearedItems]
     * 2. Sets the "cleared-by" relation in the DB.
     * 3. Commits the new [billPayTransaction]
     * 4. Updates account balances
     * @throws IllegalArgumentException if either the [clearedItems] or the [billPayTransaction] is not what
     * we expect
     */
// TODO allow checks to pay credit card bills
    override fun commitCreditCardPayment(
        clearedItems: List<ExtendedTransactionItem<ChargeAccount>>,
        billPayTransaction: Transaction,
        budgetId: UUID,
    ) =
        catchCommitErrorState {
            // require billPayTransaction is a simple real transfer between a real and a charge account
            require(billPayTransaction.draftItems.isEmpty())
            require(billPayTransaction.categoryItems.isEmpty())
            require(billPayTransaction.chargeItems.size == 1)
            require(billPayTransaction.realItems.size == 1)
            val billPayChargeTransactionItem: Transaction.Item<ChargeAccount> =
                billPayTransaction.chargeItems.first()
            val chargeAccount: ChargeAccount =
                billPayChargeTransactionItem.account
            // require clearedItems to be what we expect
            require(clearedItems.isNotEmpty())
            // all cleared items must be on the same charge account that's getting the transfer
            require(
                clearedItems.all {
                    it.item.account == chargeAccount
                },
            )
            // the amount of the clearedItems must be the same as the amount being transferred
            require(
                clearedItems
                    .fold(BigDecimal.ZERO.setScale(2)) { sum, transactionItem: ExtendedTransactionItem<ChargeAccount> ->
                        sum + transactionItem.item.amount
                    } ==
                        -billPayChargeTransactionItem.amount,
            )
            connection.transactOrThrow {
                clearedItems
                    .forEach { chargeTransactionItem: ExtendedTransactionItem<ChargeAccount> ->
                        prepareStatement(
                            """
                            |update transaction_items ti
                            |set draft_status = 'cleared'
                            |where ti.budget_id = ?
                            |and ti.id = ?
                            |and ti.draft_status = 'outstanding'
                        """.trimMargin(),
                        )
                            .use { statement ->
                                statement.setUuid(1, budgetId)
                                statement.setUuid(2, chargeTransactionItem.item.id)
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

    /**
     * Inserts the transaction records and updates the account balances in the DB.
     */
    override fun commit(
        transaction: Transaction,
        budgetId: UUID,
        saveBalances: Boolean,
    ) =
        catchCommitErrorState {
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
        buildList {
            transaction.allItems().forEach { transactionItem: Transaction.Item<*> ->
                add(transactionItem.account.id to transactionItem.amount)
            }
        }
            .forEach { (accountId: UUID, amount: BigDecimal) ->
                prepareStatement(
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

    private fun Connection.insertTransactionItemsPreparedStatement(
        transaction: Transaction,
        budgetId: UUID,
    ): PreparedStatement {
        val transactionItemCounter =
            transaction.categoryItems.size + transaction.realItems.size + transaction.draftItems.size + transaction.chargeItems.size
        val insertSql = buildString {
            var counter = transactionItemCounter
            append("insert into transaction_items (id, transaction_id, description, amount, draft_status, budget_id, account_id) values ")
            if (counter-- > 0) {
                append("(?, ?, ?, ?, ?, ?, ?)")
                while (counter-- > 0) {
                    append(", (?, ?, ?, ?, ?, ?, ?)")
                }
            }
        }
        var parameterIndex = 1
        val transactionItemInsert = prepareStatement(insertSql)
        transaction
            .allItems()
            .forEach { transactionItem: Transaction.Item<*> ->
                parameterIndex += setStandardProperties(
                    transactionItemInsert,
                    parameterIndex,
                    transaction,
                    transactionItem,
                    budgetId,
                )
                transactionItemInsert.setUuid(parameterIndex++, transactionItem.account.id)
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
        transactionItem: Transaction.Item<*>,
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

    /**
     * Saves only if we have not had an error trying to commit.
     */
    // FIXME figure out what this should really be doing and what should be done elsewhere.
    override fun save(data: BudgetData, user: User) {
        require(data.validate())
        errorState
            ?: connection.transactOrThrow {
                val generalAccountId: UUID = data.generalAccount.id
                // create user and budget if it isn't there
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
                createStagingAccountsTable()
                upsertAccountData(data.categoryAccounts, "category", data.id)
                createStagingAccountsTable()
                upsertAccountData(data.realAccounts, "real", data.id)
                createStagingAccountsTable()
                upsertAccountData(data.chargeAccounts, "charge", data.id)
                createStagingAccountsTable()
                upsertAccountData(data.draftAccounts, "draft", data.id)
            }
    }

    override fun deleteUser(userId: UUID) {
        catchCommitErrorState {
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
    }


    override fun deleteUserByLogin(login: String) {
        catchCommitErrorState {
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
        catchCommitErrorState {
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
    }

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

    /**
     * @returns a list of <accountId, activityId> where the activityId is the id of the "current" account_active_periods.
     * If there is no "current" account_active_periods record, the account is not included in the return value.
     */
    private fun Connection.getFullListOfActiveAccountsWithActivityRecords(
        accountType: String,
        budgetId: UUID,
    ): List<Pair<UUID, UUID>> =
        buildList {
            prepareStatement(
                """
    select acc.id as account_id, aap.id as activity_id
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
                .use { selectAllAccounts ->
                    selectAllAccounts.setUuid(1, budgetId)
                    selectAllAccounts.setString(2, accountType)
                    selectAllAccounts.executeQuery()
                        .use { resultSet: ResultSet ->
                            while (resultSet.next()) {
                                add(resultSet.getUuid("account_id")!! to resultSet.getUuid("activity_id")!!)
                            }
                        }
                }
        }

    override fun close() {
        keepAliveSingleThreadScheduledExecutor.close()
        connection.close()
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
