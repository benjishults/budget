package bps.budget.persistence.jdbc

import bps.budget.model.Account
import bps.budget.model.BudgetData
import bps.budget.model.CategoryAccount
import bps.budget.model.DraftAccount
import bps.budget.model.RealAccount
import bps.budget.model.Transaction
import bps.budget.persistence.BudgetDao
import bps.budget.persistence.DataConfigurationException
import bps.budget.persistence.JdbcConfig
import bps.jdbc.JdbcFixture
import bps.jdbc.transactOrNull
import bps.jdbc.transactOrThrow
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

    @Volatile
    override var connection: Connection = startConnection()
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
//                                println("DB connection died.  Restarting...")
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
        connection.transactOrNull {
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists budgets
(
    general_account_id uuid         not null unique,
    time_zone          varchar(110) not null,
    budget_name        varchar(110) not null primary key
)
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
    budget_name varchar(110)   not null references budgets (budget_name),
    primary key (id, budget_name),
    unique (name, budget_name)
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
    budget_name varchar(110)   not null references budgets (budget_name),
    primary key (id, budget_name),
    unique (name, budget_name)
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
    budget_name     varchar(110)   not null references budgets (budget_name),
    primary key (id, budget_name),
    unique (name, budget_name),
    unique (real_account_id, budget_name)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists transactions
(
    id            uuid         not null unique,
    description   varchar(110) not null default '',
    timestamp_utc timestamp    not null default now(),
    budget_name   varchar(110) not null references budgets (budget_name),
    primary key (id, budget_name)
)
                    """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_transaction_by_date
    on transactions
        (timestamp_utc desc,
         budget_name)
                    """.trimIndent(),
                )
            }
            createStatement().use { createTablesStatement: Statement ->
                createTablesStatement.executeUpdate(
                    """
create table if not exists transaction_items
(
    transaction_id      uuid           not null references transactions (id),
    description         varchar(110)   null,
    amount              numeric(30, 2) not null,
    category_account_id uuid           null references category_accounts (id),
    real_account_id     uuid           null references real_accounts (id),
    draft_account_id    uuid           null references draft_accounts (id),
    budget_name         varchar(110)   not null references budgets (budget_name),
    constraint only_one_account_per_transaction_item check
        ((real_account_id is not null and (category_account_id is null and draft_account_id is null)) or
         (category_account_id is not null and (real_account_id is null and draft_account_id is null)) or
         (draft_account_id is not null and (category_account_id is null and real_account_id is null)))
)
                """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_category_account_transaction_items_by_account
    on transaction_items
        (category_account_id,
         budget_name)
    where category_account_id is not null
                    """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_real_account_transaction_items_by_account
    on transaction_items
        (real_account_id,
         budget_name)
    where real_account_id is not null
                    """.trimIndent(),
                )
            }
            createStatement().use { createIndexStatement: Statement ->
                createIndexStatement.executeUpdate(
                    """
create index if not exists lookup_draft_account_transaction_items_by_account
    on transaction_items
        (draft_account_id,
         budget_name)
    where draft_account_id is not null
                    """.trimIndent(),
                )
            }
        }
    }

    override fun fetchTransactions(
        account: Account,
        data: BudgetData,
        limit: Int,
        offset: Int,
    ): List<Transaction> =
        connection.transactOrThrow {
            prepareStatement(
                """
                    |select t.*,
                    |       i.amount      as item_amount,
                    |       i.description as item_description,
                    |       i.category_account_id,
                    |       i.draft_account_id,
                    |       i.real_account_id
                    |from transactions t
                    |         join transaction_items i on i.transaction_id = t.id
                    |where t.id in (select tr.id
                    |               from transactions tr
                    |                        join transaction_items ti
                    |                             on tr.id = ti.transaction_id
                    |                                 and ti.budget_name = ?
                    |                                 and tr.budget_name = ?
                    |               where ti.${
                    when (account) {
                        is CategoryAccount -> "category_account_id"
                        is RealAccount -> "real_account_id"
                        is DraftAccount -> "draft_account_id"
                    }
                } = ?
                    |               order by tr.timestamp_utc desc
                    |               limit ?
                    |               offset ?)
            """.trimMargin(),
            )
                .use { statement: PreparedStatement ->
                    statement.setString(1, config.budgetName)
                    statement.setString(2, config.budgetName)
                    statement.setObject(3, account.id, OTHER)
                    statement.setInt(4, limit)
                    statement.setInt(5, offset)
                    statement.executeQuery()
                        .use { result: ResultSet ->
                            val returnValue: MutableList<Transaction> = mutableListOf()
                            var transactionId: UUID? = null
                            var transactionBuilder: Transaction.Builder? = null
                            while (result.next()) {
                                result.getObject("id", UUID::class.java)
                                    .let { uuid: UUID ->
                                        if (uuid != transactionId) {
                                            conditionallyAddCompleteTransactionToList(
                                                transactionId,
                                                returnValue,
                                                transactionBuilder,
                                            )
                                            transactionId = uuid
                                            transactionBuilder = initializeTransactionBuilder(result, uuid)
                                        }
                                    }
                                val itemAmount = result.getCurrencyAmount("item_amount")
                                val itemDescription: String? = result.getString("item_description")
                                transactionBuilder!!.categoryItemBuilders.maybeAddItemBuilder(
                                    result,
                                    itemAmount,
                                    itemDescription,
                                    data,
                                    "category",
                                ) { account: CategoryAccount ->
                                    this.categoryAccount = account
                                }
                                transactionBuilder!!.realItemBuilders.maybeAddItemBuilder(
                                    result,
                                    itemAmount,
                                    itemDescription,
                                    data,
                                    "real",
                                ) { account: RealAccount ->
                                    realAccount = account
                                }
                                transactionBuilder!!.draftItemBuilders.maybeAddItemBuilder(
                                    result,
                                    itemAmount,
                                    itemDescription,
                                    data,
                                    "draft",
                                ) { account: DraftAccount ->
                                    draftAccount = account
                                }
                            }
                            conditionallyAddCompleteTransactionToList(transactionId, returnValue, transactionBuilder)
                            returnValue.toList()
                        }
                }
        }

    private fun <T : Account> MutableList<Transaction.ItemBuilder>.maybeAddItemBuilder(
        result: ResultSet,
        itemAmount: BigDecimal,
        itemDescription: String?,
        data: BudgetData,
        type: String,
        setter: Transaction.ItemBuilder.(T) -> Unit,
    ) {
        result.getObject("${type}_account_id", UUID::class.java)
            ?.let { id: UUID ->
                add(
                    Transaction.ItemBuilder(
                        amount = itemAmount,
                        description = itemDescription,
//                        categoryAccount = data.getAccountById(id),
                    ).apply { setter(data.getAccountById(id)) },
                )
            }
    }

    private fun initializeTransactionBuilder(
        result: ResultSet,
        uuid: UUID,
    ): Transaction.Builder {
        val transactionBuilder: Transaction.Builder = Transaction.Builder()
        val description = result.getString("description")
        val time: Timestamp = result.getTimestamp("timestamp_utc")
        transactionBuilder.id = uuid
        transactionBuilder.description = description
        transactionBuilder.timestamp = time.toInstant().toKotlinInstant()
        return transactionBuilder
    }

    private fun conditionallyAddCompleteTransactionToList(
        transactionId: UUID?,
        returnValue: MutableList<Transaction>,
        transactionBuilder: Transaction.Builder?,
    ) {
        if (transactionId != null) {
            returnValue.add(transactionBuilder!!.build())
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
        budget_name     varchar(110)   not null
    )
        on commit drop
                        """.trimIndent(),
            )
        }
    }

    private fun Connection.createStagingRealAccountsTable() {
        createStatement().use { createTablesStatement: Statement ->
            createTablesStatement.executeUpdate(
                """
    create temp table if not exists staged_real_accounts
    (
        id          uuid           not null,
        gen         integer        not null generated always as identity,
        name        varchar(50)    not null,
        description varchar(110)   not null default '',
        balance     numeric(30, 2) not null default 0.0,
        budget_name varchar(110)   not null
    )
        on commit drop
                        """.trimIndent(),
            )
        }
    }

    private fun Connection.createStagingCategoryAccountsTable() {
        createStatement().use { createTablesStatement: Statement ->
            createTablesStatement.executeUpdate(
                """
    create temp table if not exists staged_category_accounts
    (
        id          uuid           not null,
        gen         integer        not null generated always as identity,
        name        varchar(50)    not null,
        description varchar(110)   not null default '',
        balance     numeric(30, 2) not null default 0.0,
        budget_name varchar(110)   not null
    )
        on commit drop
                        """.trimIndent(),
            )
        }
    }

    /**
     * Just loads top-level account info.  Details of transactions are loaded on-demand.
     * @throws DataConfigurationException if data isn't found.
     */
    override fun load(): BudgetData =
        try {
            connection.transactOrNull(
                onRollback = { ex ->
                    throw DataConfigurationException(ex.message, ex)
                },
            ) {
                val (generalAccountId: UUID, timeZone: TimeZone) =
                    prepareStatement(
                        """
                            select general_account_id, time_zone from budgets b
                            where b.budget_name = ?
                        """.trimIndent(),
                    )
                        .use { getBudget: PreparedStatement ->
                            getBudget.setString(1, config.budgetName)
                            getBudget.executeQuery()
                                .use { result: ResultSet ->
                                    result.next()
                                        .also { hadNext ->
                                            if (!hadNext)
                                                throw DataConfigurationException("Budget data not found for name: ${config.budgetName}")
                                        }
                                    result.getObject("general_account_id", UUID::class.java) to
                                            TimeZone.of(result.getString("time_zone"))
                                }
                        }
                val categoryAccounts: List<CategoryAccount> =
                    prepareStatement("select * from category_accounts where budget_name = ?")
                        .use { getCategoryAccounts: PreparedStatement ->
                            getCategoryAccounts.setString(1, config.budgetName)
                            getCategoryAccounts.executeQuery()
                                .use { result ->
                                    buildList {
                                        while (result.next()) {
                                            add(
                                                CategoryAccount(
                                                    result.getString("name"),
                                                    result.getString("description"),
                                                    result.getObject("id", UUID::class.java),
                                                    result.getCurrencyAmount("balance"),
                                                ),
                                            )
                                        }
                                    }
                                }
                        }
                val generalAccount: CategoryAccount =
                    categoryAccounts.find {
                        it.id == generalAccountId
                    }!!
                val realAccounts: List<RealAccount> =
                    prepareStatement("select * from real_accounts where budget_name = ?")
                        .use { getRealAccounts ->
                            getRealAccounts.setString(1, config.budgetName)
                            getRealAccounts.executeQuery()
                                .use { result: ResultSet ->
                                    buildList {
                                        while (result.next()) {
                                            add(
                                                RealAccount(
                                                    result.getString("name"),
                                                    result.getString("description"),
                                                    result.getObject("id", UUID::class.java),
                                                    result.getCurrencyAmount("balance"),
                                                ),
                                            )
                                        }
                                    }
                                }
                        }

                val draftAccounts: List<DraftAccount> =
                    prepareStatement("select * from draft_accounts where budget_name = ?")
                        .use { getDraftAccountsStatement ->
                            getDraftAccountsStatement.setString(1, config.budgetName)
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
                                                ),
                                            )
                                        }
                                    }
                                }
                        }
                BudgetData(
                    timeZone,
                    generalAccount,
                    categoryAccounts,
                    realAccounts,
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

    /**
     * Inserts the transaction records and updates the account balances in the DB.
     */
    override fun commit(transaction: Transaction) {
        check(transaction.validate())
        connection.transactOrNull {
            insertTransactionPreparedStatement(transaction)
                .use { insertTransaction: PreparedStatement ->
                    insertTransaction.executeUpdate()
                }
            insertTransactionItemsPreparedStatement(transaction)
                .use { insertTransactionItem: PreparedStatement ->
                    insertTransactionItem.executeUpdate()
                }
            updateBalances(transaction)
        }
    }

    private fun Connection.updateBalances(transaction: Transaction) {
        val categoryAccountUpdateStatement = """
            update category_accounts
            set balance = balance + ?
            where id = ? and budget_name = ?""".trimIndent()
        val realAccountUpdateStatement = """
            update real_accounts
            set balance = balance + ?
            where id = ? and budget_name = ?""".trimIndent()
        val draftAccountUpdateStatement = """
            update draft_accounts
            set balance = balance + ?
            where id = ? and budget_name = ?""".trimIndent()
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
                        preparedStatement.setObject(2, id, OTHER)
                        preparedStatement.setString(3, config.budgetName)
                        preparedStatement.executeUpdate()
                    }
                }
            }
    }

    private fun Connection.insertTransactionItemsPreparedStatement(transaction: Transaction): PreparedStatement {
        val transactionItemCounter =
            transaction.categoryItems.size + transaction.realItems.size + transaction.draftItems.size
        val statement = buildString {
            var counter = transactionItemCounter
            append("insert into transaction_items (transaction_id, description, amount, budget_name, category_account_id, real_account_id, draft_account_id) values ")
            if (counter-- > 0) {
                append("(?, ?, ?, ?, ?, ?, ?)")
                while (counter-- > 0) {
                    append(", (?, ?, ?, ?, ?, ?, ?)")
                }
            }
        }
        var parameterIndex = 1
        val transactionItemInsert = prepareStatement(statement)
        transaction.categoryItems.forEach { transactionItem: Transaction.Item ->
            parameterIndex += setStandardProperties(transactionItemInsert, parameterIndex, transaction, transactionItem)
            transactionItemInsert.setObject(parameterIndex++, transactionItem.categoryAccount!!.id, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
        }
        transaction.realItems.forEach { transactionItem: Transaction.Item ->
            parameterIndex += setStandardProperties(transactionItemInsert, parameterIndex, transaction, transactionItem)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setObject(parameterIndex++, transactionItem.realAccount!!.id, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
        }
        transaction.draftItems.forEach { transactionItem: Transaction.Item ->
            parameterIndex += setStandardProperties(transactionItemInsert, parameterIndex, transaction, transactionItem)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setNull(parameterIndex++, OTHER)
            transactionItemInsert.setObject(parameterIndex++, transactionItem.draftAccount!!.id, OTHER)
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
    ): Int {
        transactionItemInsert.setObject(parameterIndex, transaction.id, OTHER)
        transactionItemInsert.setString(parameterIndex + 1, transactionItem.description)
        transactionItemInsert.setBigDecimal(parameterIndex + 2, transactionItem.amount)
        transactionItemInsert.setString(parameterIndex + 3, config.budgetName)
        return 4
    }

    private fun Connection.insertTransactionPreparedStatement(transaction: Transaction): PreparedStatement {
        val insertTransaction: PreparedStatement = prepareStatement(
            """
                insert into transactions (id, description, timestamp_utc, budget_name) VALUES
                (?, ?, ?, ?)
            """.trimIndent(),
        )
        insertTransaction.setObject(1, transaction.id, OTHER)
        insertTransaction.setString(2, transaction.description)
        insertTransaction.setTimestamp(3, transaction.timestamp)
        insertTransaction.setString(4, config.budgetName)
        return insertTransaction
    }

    override fun save(data: BudgetData) {
        check(data.validate())
        // create budget if it isn't there
        connection.transactOrNull {
            val generalAccountId: UUID = data.generalAccount.id
            prepareStatement(
                """
                insert into budgets (general_account_id, time_zone, budget_name)
                values (?, ?, ?) on conflict do nothing """,
            )
                .use { createBudgetStatement: PreparedStatement ->
                    createBudgetStatement.setObject(1, generalAccountId, OTHER)
                    createBudgetStatement.setString(2, data.timeZone.id)
                    createBudgetStatement.setString(3, config.budgetName)
                    createBudgetStatement.executeUpdate()
                    // TODO set up logging to a file and have the file printed to console on quit
//                        .also { if (it == 0)  }
                }
            // create accounts that aren't there and update those that are
            createStagingCategoryAccountsTable()
            upsertAccountData(data.categoryAccounts, CATEGORY_ACCOUNT_TABLE_NAME)
            createStagingRealAccountsTable()
            upsertAccountData(data.realAccounts, REAL_ACCOUNT_TABLE_NAME)
            createStagingDraftAccountsTable()
            upsertAccountData(data.draftAccounts, DRAFT_ACCOUNT_TABLE_NAME)
            // TODO mark deleted accounts as in-active
            //      see account_active_periods which is currently unused
        }
    }

    /**
     * Must be called within a transaction with manual commits
     */
    private fun Connection.upsertAccountData(accounts: List<Account>, tableName: String) {
        accounts.forEach { account ->
            prepareStatement(
                """
                insert into staged_$tableName (id, name, description, balance, ${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) "real_account_id, " else ""}budget_name)
                VALUES (?, ?, ?, ?,${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) " ?," else ""} ?)
                on conflict do nothing
            """.trimIndent(),
            )
                .use { createStagedAccountStatement: PreparedStatement ->
                    var parameterIndex = 1
                    createStagedAccountStatement.setObject(parameterIndex++, account.id, OTHER)
                    createStagedAccountStatement.setString(parameterIndex++, account.name)
                    createStagedAccountStatement.setString(parameterIndex++, account.description)
                    createStagedAccountStatement.setBigDecimal(parameterIndex++, account.balance)
                    if (tableName == DRAFT_ACCOUNT_TABLE_NAME) {
                        createStagedAccountStatement.setObject(
                            parameterIndex++,
                            (account as DraftAccount).realCompanion.id,
                            OTHER,
                        )
                    }
                    createStagedAccountStatement.setString(parameterIndex++, config.budgetName)
                    createStagedAccountStatement.executeUpdate()
                }
            prepareStatement(
                """
                merge into $tableName as t
                    using staged_$tableName as s
                    on (t.id = s.id or t.name = s.name) and t.budget_name = s.budget_name
                    when matched then
                        update
                        set name = s.name,
                            description = s.description,
                            balance = s.balance
                    when not matched then
                        insert (id, name, description, balance, ${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) "real_account_id, " else ""}budget_name)
                        values (s.id, s.name, s.description, s.balance, ${if (tableName == DRAFT_ACCOUNT_TABLE_NAME) "s.real_account_id, " else ""}s.budget_name);
                """.trimIndent(),
            )
                .use { createAccountStatement: PreparedStatement ->
                    createAccountStatement.executeUpdate()
                }
        }
    }

    override fun close() {
        keepAliveSingleThreadScheduledExecutor.close()
        super.close()
    }

    companion object {
        const val CATEGORY_ACCOUNT_TABLE_NAME = "category_accounts"
        const val REAL_ACCOUNT_TABLE_NAME = "real_accounts"
        const val DRAFT_ACCOUNT_TABLE_NAME = "draft_accounts"
    }

}

//fun Instant.toLocalDateTime(timeZone: TimeZone): LocalDateTime =
//    ZonedDateTime
//        .ofInstant(this, timeZone.toZoneId())
//        .toLocalDateTime()

/**
 * This assumes that the DB [Timestamp] is stored in UTC.
 */
fun Timestamp.toLocalDateTime(timeZone: TimeZone): LocalDateTime =
    this
        .toInstant()
        .toKotlinInstant()
        .toLocalDateTime(timeZone)
